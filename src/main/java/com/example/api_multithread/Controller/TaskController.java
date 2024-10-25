package com.example.api_multithread.Controller;
import com.example.api_multithread.model.Task;
import com.example.api_multithread.repository.TaskRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

        @Autowired
        private TaskRepository taskRepository;

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
        public Task createTask(@RequestBody Task task) {
            return taskRepository.save(task);
        }

        @PutMapping("/{id}")
        public ResponseEntity<Task> updateTask(@PathVariable Long id, @RequestBody Task task) {
            Optional<Task> taskExist = taskRepository.findById(id);
            if (taskExist.isPresent()) {
                task.setId(id);
                return ResponseEntity.ok(taskRepository.save(task));
            } else {
                return ResponseEntity.notFound().build();
            }
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<Void> deleteTask(@PathVariable Long id) {
            if (taskRepository.existsById(id)) {
                taskRepository.deleteById(id);
                return ResponseEntity.noContent().build();
            } else {
                return ResponseEntity.notFound().build();
            }
        }
}
