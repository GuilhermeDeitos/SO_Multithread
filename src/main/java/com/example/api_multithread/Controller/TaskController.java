package com.example.api_multithread.Controller;
import com.example.api_multithread.model.Task;
import com.example.api_multithread.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

        @Autowired
        private TaskRepository taskRepository;

        // Pool de threads com 10 threads
        private final ExecutorService executorService = Executors.newFixedThreadPool(10);

        // Semáforo que permite até 5 threads simultâneas
        private final Semaphore createSemaphore = new Semaphore(5);

        // Semáforo que permite apenas 1 thread de cada vez para delete e put
        private final Semaphore editSemaphore = new Semaphore(1);

        @GetMapping
        public List<Task> listTasks() {
            return taskRepository.findAll();
        }

        @GetMapping("/{id}")
        public ResponseEntity<Task> findTaskById(@PathVariable Long id) {
            Optional<Task> task = taskRepository.findById(id);
            return task.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
        }

    @PostMapping
    public ResponseEntity<String> createTask(@RequestBody Task task) {
        AtomicBoolean wasInterrupted = new AtomicBoolean(false);

        // Submeter a criação da tarefa para execução em uma thread separada
        executorService.submit(() -> {
            try {
                createSemaphore.acquire();
                // Seção crítica (criação da tarefa)
                Task savedTask = taskRepository.save(task);
                System.out.println("Task criada: " + savedTask.getId() + " - " + Thread.currentThread().getName());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Erro ao adquirir semáforo: " + e.getMessage());
                wasInterrupted.set(true);
            } finally {
                // Liberar o "permite" do semáforo
                createSemaphore.release();
            }
        });

        String responseMessage = (wasInterrupted.get() ? "Erro ao criar a tarefa" : "Tarefa criada") + " - " + Thread.currentThread().getName() + task ;

        return ResponseEntity.ok(responseMessage);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody Task task) {
        // Usar semáforo para garantir que apenas uma thread possa editar ao mesmo tempo
        try {
            editSemaphore.acquire();
            Optional<Task> taskExist = taskRepository.findById(id);
            if (taskExist.isPresent()) {
                task.setId(id);
                return ResponseEntity.ok(taskRepository.save(task));
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(null);
        } finally {
            // Liberar o "permite" do semáforo de edição
            editSemaphore.release();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
        // Usar semáforo para garantir que apenas uma thread possa deletar ao mesmo tempo
        try {
            editSemaphore.acquire();
            if (taskRepository.existsById(id)) {
                taskRepository.deleteById(id);
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(null);
        } finally {
            // Liberar o "permite" do semáforo de edição
            editSemaphore.release();
        }
    }
}
