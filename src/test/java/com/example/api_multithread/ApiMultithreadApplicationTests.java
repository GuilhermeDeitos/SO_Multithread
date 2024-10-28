package com.example.api_multithread;

import com.example.api_multithread.Controller.TaskController;
import com.example.api_multithread.model.Task;
import com.example.api_multithread.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;

import java.util.Optional;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class ApiMultithreadApplicationTests {

	@Autowired
	private TaskController taskController;

	@MockBean
	private TaskRepository taskRepository;

	@BeforeEach
	void setUp() {
		// Configurações de inicialização antes de cada teste, caso necessário
	}

	@Test
	public void testCreateTask() {
		Task task = new Task();
		task.setId(1L);

		when(taskRepository.save(Mockito.any(Task.class))).thenReturn(task);

		ResponseEntity<String> response = taskController.createTask(task);

		assertEquals(200, response.getStatusCodeValue());
		assertTrue(response.getBody().contains("Tarefa criada com sucesso"));
	}

	@Test
	public void testUpdateTask_Success() {
		Task task = new Task();
		task.setId(1L);

		when(taskRepository.existsById(1L)).thenReturn(true);
		when(taskRepository.save(task)).thenReturn(task);

		ResponseEntity<String> response = taskController.updateTask(1L, task);

		assertEquals(200, response.getStatusCodeValue());
		assertTrue(response.getBody().contains("Tarefa atualizada com sucesso"));
	}

	@Test
	public void testUpdateTask_NotFound() {
		Task task = new Task();
		task.setId(1L);

		when(taskRepository.existsById(1L)).thenReturn(false);

		ResponseEntity<String> response = taskController.updateTask(1L, task);

		assertEquals(404, response.getStatusCodeValue());
	}

	@Test
	public void testDeleteTask_Success() {
		when(taskRepository.existsById(1L)).thenReturn(true);
		doNothing().when(taskRepository).deleteById(1L);

		ResponseEntity<String> response = taskController.deleteTask(1L);

		assertEquals(200, response.getStatusCodeValue());
		assertTrue(response.getBody().contains("Tarefa deletada com sucesso"));
	}

	@Test
	public void testDeleteTask_NotFound() {
		when(taskRepository.existsById(1L)).thenReturn(false);

		ResponseEntity<String> response = taskController.deleteTask(1L);

		assertEquals(404, response.getStatusCodeValue());
	}

	@Test
	public void testSimultaneousDeleteOperations() throws InterruptedException, ExecutionException {
		when(taskRepository.existsById(1L)).thenReturn(true);
		when(taskRepository.existsById(2L)).thenReturn(true);
		doNothing().when(taskRepository).deleteById(1L);
		doNothing().when(taskRepository).deleteById(2L);

		ExecutorService executor = Executors.newFixedThreadPool(2);

		Callable<ResponseEntity<String>> deleteTask1 = () -> taskController.deleteTask(1L);
		Callable<ResponseEntity<String>> deleteTask2 = () -> taskController.deleteTask(2L);

		Future<ResponseEntity<String>> result1 = executor.submit(deleteTask1);
		Future<ResponseEntity<String>> result2 = executor.submit(deleteTask2);

		ResponseEntity<String> response1 = result1.get();
		ResponseEntity<String> response2 = result2.get();

		assertEquals(200, response1.getStatusCodeValue());
		assertEquals(200, response2.getStatusCodeValue());

		executor.shutdown();
	}

	@Test
	public void testSimultaneousEditOperations() throws InterruptedException, ExecutionException {
		when(taskRepository.existsById(1L)).thenReturn(true);
		when(taskRepository.existsById(2L)).thenReturn(true);
		when(taskRepository.save(Mockito.any(Task.class))).thenAnswer(invocation -> invocation.getArgument(0));

		ExecutorService executor = Executors.newFixedThreadPool(2);

		Callable<ResponseEntity<String>> editTask1 = () -> {
			Task task1 = new Task();
			task1.setId(1L);
			task1.setTitle("Task 1 Updated");
			return taskController.updateTask(1L, task1);
		};

		Callable<ResponseEntity<String>> editTask2 = () -> {
			Task task2 = new Task();
			task2.setId(2L);
			task2.setTitle("Task 2 Updated");
			return taskController.updateTask(2L, task2);
		};

		Future<ResponseEntity<String>> result1 = executor.submit(editTask1);
		Future<ResponseEntity<String>> result2 = executor.submit(editTask2);

		ResponseEntity<String> response1 = result1.get();
		ResponseEntity<String> response2 = result2.get();


		assertEquals(200, response1.getStatusCodeValue());
		assertEquals(429, response2.getStatusCodeValue());
		assertTrue(response1.getBody().contains("Tarefa atualizada com sucesso"));
		assertTrue(response2.getBody().contains("Limite de threads atingido. Tente novamente mais tarde."));

		executor.shutdown();
	}

	@Test
	public void testSimultaneousTaskCreation() throws InterruptedException, ExecutionException {
		// Configurar o comportamento do mock para simular o salvamento da tarefa
		for (long i = 1; i <= 5; i++) {
			Task task = new Task();
			task.setId(i); // Atribuindo um ID para o mock
			when(taskRepository.save(Mockito.any(Task.class))).thenReturn(task);
		}

		ExecutorService executor = Executors.newFixedThreadPool(7);

		Callable<ResponseEntity<String>> createTask = () -> {
			Task newTask = new Task();
			newTask.setTitle("Nova Task");
			return taskController.createTask(newTask);
		};

		Future<ResponseEntity<String>>[] results = new Future[7];
		for (int i = 0; i < 7; i++) {
			results[i] = executor.submit(createTask);
		}

		int successCount = 0;
		for (int i = 0; i < 7; i++) {
			ResponseEntity<String> response = results[i].get();
			if (response.getStatusCodeValue() == 200) {
				successCount++;
			}
		}

		// O número de respostas bem-sucedidas deve ser igual ao número de threads permitidas
		assertEquals(5, successCount, "Deve haver exatamente 5 tarefas criadas com sucesso.");

		executor.shutdown();
	}


}
