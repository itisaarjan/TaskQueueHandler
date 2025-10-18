package com.example.TaskDBService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class TaskDBService {

    private final TaskRepository taskRepository;

    public TaskDBService(final TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public TaskDB saveTask(final TaskDB taskDB) {
        return taskRepository.save(taskDB);
    }

    @Transactional(readOnly = true)
    public Optional<TaskDB> findById(final String id) {
        return taskRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<TaskDB> findAll() {
        return taskRepository.findAll();
    }

    @Transactional
    public void updateStatus(final String id, final String status, final String resultUrl) {
        taskRepository.findById(id).ifPresent(task -> {
            task.setStatus(status);
            if (resultUrl != null) {
                task.setResultUrl(resultUrl);
            }
            taskRepository.save(task);
        });
    }

    @Transactional
    public void markTaskCompleted(final String id, final String resultUrl) {
        taskRepository.findById(id).ifPresent(task -> {
            task.setStatus("completed");
            task.setResultUrl(resultUrl);
            task.setCompletedAt(java.time.Instant.now());
            taskRepository.save(task);
        });
    }

    @Transactional
    public void deleteTask(final String id) {
        taskRepository.deleteById(id);
    }
}
