package com.example.api_multithread.Controller;

import com.example.api_multithread.model.Task;
import com.example.api_multithread.repository.TaskRepository;
import com.example.api_multithread.model.TaskSemaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.*;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "http://localhost:3000/") // Configuração CORS para este controlador
public class TaskController {

    @Autowired
    private TaskRepository taskRepository;

    // Pool de threads com 10 threads
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // Semáforo que permite até 5 threads simultâneas para criar tarefas
    private final Semaphore createSemaphore = new Semaphore(5);

    // Semáforo que permite apenas 1 thread de cada vez para operações de edição e exclusão
    private final TaskSemaphore editSemaphore = new TaskSemaphore(null);

    // ---- Operações de leitura (com multithreading) ----


    @GetMapping
    public List<Task> listTasks() {
        // Submeter a busca das tarefas para execução em uma thread separada
        Future<List<Task>> future = executorService.submit(() -> taskRepository.findAll());

        try {
            // Esperar o resultado da execução e retornar esse resultado
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> findTaskById(@PathVariable Long id) {
        // Submeter a busca da tarefa por ID para execução em uma thread separada
        Future<Optional<Task>> future = executorService.submit(() -> taskRepository.findById(id));

        try {
            Optional<Task> task = future.get();
            return task.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    // ---- Operação de criação (com semáforos e multithreading) ----

    @PostMapping
    public ResponseEntity<String> createTask(@RequestBody Task task) {
        // Tentar adquirir o semáforo antes de criar a tarefa
        if (!createSemaphore.tryAcquire()) {
            System.out.println("Limite de threads atingido");
            return ResponseEntity.status(429).body("Limite de threads atingido. Tente novamente mais tarde.");
        }

        // Tentar adquirir o semáforo antes de submeter a criação da tarefa
        try {
            executorService.submit(() -> {
                try {
                    Task savedTask = taskRepository.save(task);
                    System.out.println("Task criada: " + savedTask.getId() + " - " + Thread.currentThread().getName());
                } catch (Exception e) {
                    System.out.println("Erro ao criar a tarefa: " + e.getMessage());
                }
            });
        } finally {
            // Liberar o semáforo após a submissão da tarefa
            createSemaphore.release();
        }
        return ResponseEntity.ok("Tarefa criada com sucesso - " + task.getTitle());
    }

    @PostMapping("/acquire")
    public ResponseEntity<String> requestEditTask(@RequestBody Task task) {
        // Usar semáforo para garantir que apenas uma thread possa editar ao mesmo tempo
        System.out.println("Tentando adquirir semáforo acquire");
        if(editSemaphore.isLocked()) {
            return ResponseEntity.status(429).body("Alguém já está mexendo na tarefa. Tente novamente mais tarde.");
        }
        editSemaphore.acquire(task);
        return ResponseEntity.ok("Solicitação de alteração da tarefa " + task.getId() + " recebida.");

    }

    @PostMapping("/release")
    public ResponseEntity<String> releaseEditTask() {
        // Usar semáforo para garantir que apenas uma thread possa editar ao mesmo tempo
        editSemaphore.release();
        return ResponseEntity.ok("Tarefa liberada.");

    }

    // ---- Operação de edição (com semáforo para garantir exclusividade) ----

    @PutMapping("/{id}")
    public ResponseEntity<String> updateTask(@PathVariable Long id, @RequestBody Task task) {
        System.out.println("Tentando adquirir semáforo de edição");

        if (editSemaphore.isLocked()) {
            System.out.println("Tarefa em edição");
            return ResponseEntity.status(429).body("Tarefa em edição. Tente novamente mais tarde.");
        }

        if (taskRepository.existsById(id)) {
            task.setId(id);
            editSemaphore.acquire(task);
            taskRepository.save(task);
            editSemaphore.release();

            return ResponseEntity.ok("Tarefa atualizada com sucesso - ID: " + id);
        } else {
            return ResponseEntity.status(404).body("Erro: Tarefa não encontrada.");
        }

    }


    // ---- Operação de exclusão (com semáforo para garantir exclusividade) ----

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTask(@PathVariable Long id) {
        // Usar semáforo para garantir que apenas uma thread possa editar simultaneamente
        try {
            System.out.println("Tentando adquirir semáforo de edição");
            if (editSemaphore.isLocked()) {
                System.out.println("Tarefa em edição");
                return ResponseEntity.status(429).body("Tarefa em edição. Tente novamente mais tarde.");
            }
            Task task = taskRepository.findById(id).orElse(null);
            if (task != null) {
                editSemaphore.acquire(task);
                taskRepository.deleteById(id);
                editSemaphore.release();
                return ResponseEntity.ok("Tarefa deletada com sucesso - ID: " + id);
            } else {
                return ResponseEntity.status(404).body("Erro: Tarefa não encontrada.");
            }
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erro ao deletar a tarefa: " + e.getMessage());
        }
    }

    // ---- Operações sem multithreading e sem semáforo (para fins de comparação) ----
    @PostMapping("/singlethread")
    public ResponseEntity<String> createTaskSingleThread(@RequestBody Task task) {
        try {

            Task savedTask = taskRepository.save(task);
            return ResponseEntity.ok("Tarefa criada com sucesso - " + savedTask.getTitle());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("Erro ao criar a tarefa: " + e.getMessage());
        }
    }

    @PutMapping("/singlethread/{id}")
    public ResponseEntity<String> updateTaskNoSemaphore(@PathVariable Long id, @RequestBody Task task) {
        if (taskRepository.existsById(id)) {
            task.setId(id);
            taskRepository.save(task);
            return ResponseEntity.ok("Tarefa atualizada com sucesso - ID: " + id);
        } else {
            return ResponseEntity.status(404).body("Erro: Tarefa não encontrada.");
        }
    }

    @DeleteMapping("/singlethread/{id}")
    public ResponseEntity<String> deleteTaskNoSemaphore(@PathVariable Long id) {
        if (taskRepository.existsById(id)) {
            taskRepository.deleteById(id);
            return ResponseEntity.ok("Tarefa deletada com sucesso - ID: " + id);
        } else {
            return ResponseEntity.status(404).body("Erro: Tarefa não encontrada.");
        }
    }

    @GetMapping("/singlethread")
    public List<Task> listTasksSingleThread() {
        return taskRepository.findAll();
    }

    @GetMapping("/singlethread/{id}")
    public ResponseEntity<Task> findTaskByIdSingleThread(@PathVariable Long id) {
        Optional<Task> task = taskRepository.findById(id);
        return task.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    // ---- Operações de leitura com ordenação (com multithreading sem sincronização) ----
    // Função de ordenação reutilizável
    private List<Task> sortTasks(List<Task> tasks) {
        tasks.sort(Comparator.comparing(Task::getTitle));
        return tasks;
    }

    // Função de ordenação multithread sincronizado
    @GetMapping("/order")
    public List<Task> listTasksOrder() throws InterruptedException, ExecutionException {
        // Inicializar o timer
        long startTime = System.currentTimeMillis();

        // Obter a lista de tarefas
        List<Task> tasks = listTasksSingleThread();

        // Definir o número de threads baseado no tamanho da lista e no pool
        int numThreads = Math.min(8, Runtime.getRuntime().availableProcessors());
        int chunkSize = (int) Math.ceil((double) tasks.size() / numThreads);

        // Executor para paralelizar as tarefas
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        List<Future<List<Task>>> futures = new ArrayList<>();

        // Dividir as tarefas em sublistas e ordenar paralelamente
        for (int i = 0; i < numThreads; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, tasks.size());

            if (start < end) { // Verificar se o índice de início está dentro do intervalo
                List<Task> sublist = tasks.subList(start, end);
                futures.add(executor.submit(() -> {
                    sublist.sort(Comparator.comparing(Task::getTitle)); // Ordenação dentro da thread
                    return sublist;
                }));
            }
        }

        // Coletar os resultados
        List<Task> sortedTasks = new ArrayList<>();
        for (Future<List<Task>> future : futures) {
            sortedTasks.addAll(future.get());
        }

        // Fechar o executor
        executor.shutdown();

        // Fazer a mesclagem final das listas ordenadas
        sortedTasks.sort(Comparator.comparing(Task::getTitle));

        // Finalizar o timer
        long endTime = System.currentTimeMillis();

        //Mostrar a lista de tarefas ordenadas
        //this.showTasks(sortedTasks);

        System.out.println("Tempo de execução: " + (endTime - startTime) + "ms");

        return sortedTasks;
    }

    // Função de ordenação multithread sem sincronização
    @GetMapping("/order/unsynchronized")
    public List<Task> listTasksOrderUnsynchronized() throws InterruptedException {
        // Inicializar o timer
        long startTime = System.currentTimeMillis();

        // Obter a lista de tarefas
        List<Task> tasks = listTasksSingleThread();

        // Definir o número de threads baseado no tamanho da lista e no pool
        int numThreads = Math.min(8, Runtime.getRuntime().availableProcessors());
        int chunkSize = (int) Math.ceil((double) tasks.size() / numThreads);

        // Executor para paralelizar as tarefas
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);

        // Submeter as tarefas de ordenação sem esperar o retorno
        for (int i = 0; i < numThreads; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, tasks.size());

            // Verificar se o índice de início está dentro do intervalo
            if (start < tasks.size()) {
                List<Task> sublist = tasks.subList(start, end);
                executor.submit(() -> {
                    try {
                        // Simular um processamento demorado
                        Thread.sleep(1000); // Atraso reduzido para 1 segundo
                        sublist.sort(Comparator.comparing(Task::getTitle)); // Ordenação dentro da thread
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Manter o estado de interrupção
                    }
                });
            }
        }

        // Mesclagem final sem garantias de que as sublistas estão completamente ordenadas
        List<Task> sortedTasks = new ArrayList<>(tasks);

        // Fechar o executor sem aguardar a conclusão das tarefas e encerrar as execuções existente
        executor.shutdownNow();

        // Finalizar o timer
        long endTime = System.currentTimeMillis();

        // Mostrar a lista de tarefas ordenadas (ou não)
        //this.showTasks(sortedTasks);

        System.out.println("Tempo de execução: " + (endTime - startTime) + "ms");

        // Retornar a lista possivelmente desordenada ou parcialmente ordenada
        return sortedTasks;
    }

    private void showTasks(List<Task> tasks) {
        System.out.println("Tarefas ordenadas:");
        for (Task task : tasks) {
            System.out.println(task.getTitle()+", id:"+task.getId());
        }
    }

    // Função de ordenação singlethread reutilizando a função de ordenação
    @GetMapping("/order/singlethread")
    public List<Task> listTasksSingleThreadOrder() {
        List<Task> tasks = this.listTasksSingleThread();

        // Inicializar o timer
        long startTime = System.currentTimeMillis();
        tasks = sortTasks(tasks);
        // Finalizar o timer
        long endTime = System.currentTimeMillis();

        System.out.println("Tempo de execução: " + (endTime - startTime) + "ms");
        //Mostrar a lista de tarefas ordenadas
        //this.showTasks(tasks);

        System.out.println("Tempo de execução: " + (endTime - startTime) + "ms");

        return tasks;
    }

}
