package com.example.api_multithread.Controller;

import com.example.api_multithread.model.Task;
import com.example.api_multithread.repository.TaskRepository;
import com.example.api_multithread.model.TaskSemaphore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "http://localhost:3000") // Configuração CORS para este controlador
public class TaskController {

    @Autowired
    private TaskRepository taskRepository;

    // Pool de threads com 10 threads
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    // Semáforo que permite até 5 threads simultâneas para criar tarefas
    private final Semaphore createSemaphore = new Semaphore(5);

    // Semáforo que permite apenas 1 thread de cada vez para operações de edição e exclusão
    private final TaskSemaphore editSemaphore = new TaskSemaphore(null);

    // ---- Operações de leitura (sem semáforos e sem multithreading) ----

    // ---- Operações de leitura (com multithreading) ----

    @GetMapping
    public List<Task> listTasks() {
        // Submeter a busca das tarefas para execução em uma thread separada
        Future<List<Task>> future = executorService.submit(() -> {
            System.out.println("Buscando tarefas - " + Thread.currentThread().getName());
            return taskRepository.findAll();
        });

        try {
            // Esperar o resultado da execução em outra thread
            return future.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> findTaskById(@PathVariable Long id) {
        // Submeter a busca da tarefa por ID para execução em uma thread separada
        Future<Optional<Task>> future = executorService.submit(() -> {
            System.out.println("Buscando tarefa por ID - " + Thread.currentThread().getName());
            return taskRepository.findById(id);
        });

        try {
            Optional<Task> task = future.get();
            return task.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        }
    }

    // ---- Operações de criação (com semáforos e multithreading) ----

    @PostMapping
    public ResponseEntity<String> createTask(@RequestBody Task task) {
        // Tentar adquirir o semáforo antes de criar a tarefa
        if (!createSemaphore.tryAcquire()) {
            System.out.println("Limite de threads atingido");
            return ResponseEntity.status(429).body("Limite de threads atingido. Tente novamente mais tarde.");
        }

        // Submeter a criação da tarefa para execução em uma thread separada
        executorService.submit(() -> {
            try {
                // Seção crítica (criação da tarefa)
                Task savedTask = taskRepository.save(task);
                System.out.println("Task criada: " + savedTask.getId() + " - " + Thread.currentThread().getName());
            } catch (Exception e) {
                System.out.println("Erro ao criar a tarefa: " + e.getMessage());
            } finally {
                // Liberar o "permite" do semáforo
                createSemaphore.release();
            }
        });

        return ResponseEntity.ok("Tarefa criada com sucesso - " + task.getTitle());
    }

    // ---- Operações de edição (com semáforo para garantir exclusividade) ----

    @PutMapping("/{id}")
    public ResponseEntity<String> updateTask(@PathVariable Long id, @RequestBody Task task) {
        // Usar semáforo para garantir que apenas uma thread possa editar simultaneamente
        try {
            System.out.println("Tentando adquirir semáforo de edição");
            if(editSemaphore.isLocked()) {
                System.out.println("Tarefa em edição");
                return ResponseEntity.status(429).body("Tarefa em edição. Tente novamente mais tarde.");
            }
            editSemaphore.acquire(task);

            if (taskRepository.existsById(id)) {
                task.setId(id);
                taskRepository.save(task);
                return ResponseEntity.ok("Tarefa atualizada com sucesso - ID: " + id);
            } else {
                return ResponseEntity.status(404).body("Erro: Tarefa não encontrada.");
            }
        } finally {
            // Liberar o "permite" do semáforo de edição
            editSemaphore.release();
        }
    }

    // ---- Operações de exclusão (com semáforo para garantir exclusividade) ----

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTask(@PathVariable Long id) {
        // Usar semáforo para garantir que apenas uma thread possa editar simultaneamente
        try {
            System.out.println("Tentando adquirir semáforo de edição");
            if(editSemaphore.isLocked()) {
                System.out.println("Tarefa em edição");
                return ResponseEntity.status(429).body("Tarefa em edição. Tente novamente mais tarde.");
            }
            editSemaphore.acquire(new Task());

            if (taskRepository.existsById(id)) {
                taskRepository.deleteById(id);
                return ResponseEntity.ok("Tarefa deletada com sucesso - ID: " + id);
            } else {
                return ResponseEntity.status(404).body("Erro: Tarefa não encontrada.");
            }
        } finally {
            // Liberar o "permite" do semáforo de edição
            editSemaphore.release();
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
}
