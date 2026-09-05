package com.auth.service;

import com.auth.dto.UserDto;
import com.auth.entity.User;
import com.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public User saveUser(UserDto dto) {
        try {
            Optional<User> byUsername = userRepository.findByUsername(dto.getUsername());
            if (byUsername.isPresent()){
                throw new RuntimeException("User already exists");
            }
            User user = User.builder().username(dto.getUsername())
                    .password(passwordEncoder.encode(dto.getPassword()))
                    .role(dto.getRoles()).build();

            User save = userRepository.save(user);
            return save;
        }catch (Exception e){
            e.printStackTrace();
            return null;
        }
    }

    public User fetchUserByUsername(String username) {
        return userRepository.findByUsername(username).orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }



}
