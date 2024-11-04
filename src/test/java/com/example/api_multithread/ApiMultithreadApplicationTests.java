package com.example.api_multithread;

import com.example.api_multithread.Controller.TaskController;
import com.example.api_multithread.model.Task;
import com.example.api_multithread.model.TaskSemaphore;
import com.example.api_multithread.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
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
		when(taskRepository.save(Mockito.any(Task.class))).thenReturn(task);

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
		Task task = new Task();
		task.setId(1L);

		when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
		doNothing().when(taskRepository).deleteById(1L);

		ResponseEntity<String> response = taskController.deleteTask(1L);

		assertEquals(200, response.getStatusCodeValue());
		assertTrue(response.getBody().contains("Tarefa deletada com sucesso - ID: 1"));
	}

	@Test
	public void testDeleteTask_NotFound() {
		when(taskRepository.existsById(1L)).thenReturn(false);

		ResponseEntity<String> response = taskController.deleteTask(1L);

		assertEquals(404, response.getStatusCodeValue());
	}


	private String generateRandomString(int length) {
		String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			int index = ThreadLocalRandom.current().nextInt(0, characters.length());
			sb.append(characters.charAt(index));
		}
		return sb.toString();
	}

	private List<Task> generateTasks(int count) {
		List<Task> tasks = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Task task = new Task();
			task.setId((long) i);
			int random = ThreadLocalRandom.current().nextInt(1, 15);
			task.setTitle(generateRandomString(random));
			tasks.add(task);
		}
		return tasks;
	}

	@Test
	public void testSortTasksSingleThread() {
		List<Task> tasks = generateTasks(100000);
		when(taskRepository.findAll()).thenReturn(tasks);
		List<Task> sortedTasks = taskController.listTasksSingleThreadOrder();
		for (int i = 0; i < sortedTasks.size() - 1; i++) {
			assertTrue(sortedTasks.get(i).getTitle().compareTo(sortedTasks.get(i + 1).getTitle()) <= 0);
		}
	}

	@Test
	public void testSortTasksMultiThread() throws ExecutionException, InterruptedException {
		List<Task> tasks = generateTasks(100000);
		when(taskRepository.findAll()).thenReturn(tasks);

		List<Task> sortedTasks = taskController.listTasksOrder();

		for (int i = 0; i < sortedTasks.size() - 1; i++) {
			assertTrue(sortedTasks.get(i).getTitle().compareTo(sortedTasks.get(i + 1).getTitle()) <= 0);
		}
	}

	@Test
	public void testSortTasksMultithreadUnsynchronized() throws ExecutionException, InterruptedException {
		List<Task> tasks = generateTasks(100);
		when(taskRepository.findAll()).thenReturn(tasks);

		List<Task> sortedTasks = taskController.listTasksOrderUnsynchronized();

		boolean isOrdered = true;
		for (int i = 0; i < sortedTasks.size() - 1; i++) {
			if (sortedTasks.get(i).getTitle().compareTo(sortedTasks.get(i + 1).getTitle()) > 0) {
				isOrdered = false;
				break;
			}
		}

		assertFalse(isOrdered, "A lista deve estar desordenada devido à falta de sincronização");
	}
}
