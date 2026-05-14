package com.example.users;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private NotificationService notificationService;

    public User findById(Long id) {
        return userRepository.findById(id).orElseThrow();
    }

    public void notify(Long id, String message) {
        notificationService.send(findById(id), message);
    }
}
