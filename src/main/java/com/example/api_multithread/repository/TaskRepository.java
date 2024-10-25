package com.example.api_multithread.repository;
import com.example.api_multithread.model.Task;
import org.springframework.data.jpa.repository.JpaRepository;
public interface TaskRepository extends JpaRepository<Task, Long> {
}
