package com.example.users;

import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public UserService(UserRepository userRepository, NotificationService notificationService) {
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    public void notify(Long id, String message) {
        notificationService.send(findById(id), message);
    }
}
