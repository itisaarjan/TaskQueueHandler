package com.example.TaskDBService;

public class TaskDBService {
    private final TaskRepository taskRepository;

    public TaskDBService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

}
