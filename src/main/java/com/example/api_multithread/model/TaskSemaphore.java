package com.example.api_multithread.model;

import java.util.Objects;

//IMplementação de um semáforo binário para controlar o acesso a uma task
public class TaskSemaphore implements Runnable {
    private Task task; //Armazena a task para posteriormente verificar se essa task está sendo editada
    private boolean isLocked = false; //Variável que indica se a task está sendo editada

    public TaskSemaphore(Task task) {
        this.task = task;
    }

    @Override
    public void run() {
        //Implementação do semáforo
    }

    public Task getTask() {
        return task;
    }

    public void acquire(Task task) {
        //Implementação do semáforo
        if(this.task == null || Objects.equals(this.task.getId(), task.getId())) {
            isLocked = true;
        }

    }

    public void release() {
        //Implementação do semáforo
        this.isLocked = false;
        this.task = null;
    }

    public boolean isLocked() {
        return isLocked;
    }
}
