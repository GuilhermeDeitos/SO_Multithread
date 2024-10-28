package com.example.api_multithread.Controller;

import com.example.api_multithread.model.Task;
import com.example.api_multithread.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/tasks")
@CrossOrigin(origins = "http://localhost:3000") // Configuração CORS para este controlador
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





    @PutMapping("/{id}")
    public ResponseEntity<String> updateTask(@PathVariable Long id, @RequestBody Task task) {
        // Usar semáforo para garantir que apenas uma thread possa editar ao mesmo tempo
        try {
            System.out.println("Tentando adquirir semáforo de edição");
            if(editSemaphore.availablePermits() == 0) {
                System.out.println("Limite de threads atingido");
                return ResponseEntity.status(429).body("Limite de threads atingido. Tente novamente mais tarde.");
            }
            editSemaphore.acquire();

            if (taskRepository.existsById(id)) {
                task.setId(id);
                Task updatedTask = taskRepository.save(task);
                return ResponseEntity.ok("Tarefa atualizada com sucesso - ID: " + updatedTask.getId());
            } else {
                return ResponseEntity.status(404).body("Erro: Tarefa não encontrada.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body("Erro ao atualizar a tarefa.");
        } finally {
            // Liberar o "permite" do semáforo de edição
            editSemaphore.release();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteTask(@PathVariable Long id) {
        // Usar semáforo para garantir que apenas uma thread possa deletar ao mesmo tempo
        try {
            System.out.println("Tentando adquirir semáforo de remoção");
            if(editSemaphore.availablePermits() == 0) {
                System.out.println("Limite de threads atingido");
                return ResponseEntity.status(429).body("Limite de threads atingido. Tente novamente mais tarde.");
            }
            editSemaphore.acquire();

            if (taskRepository.existsById(id)) {
                taskRepository.deleteById(id);
                return ResponseEntity.ok("Tarefa deletada com sucesso - ID: " + id);
            } else {
                return ResponseEntity.status(404).body("Erro: Tarefa não encontrada.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body("Erro ao deletar a tarefa.");
        } finally {
            // Liberar o "permite" do semáforo de edição
            editSemaphore.release();
        }
    }
}
