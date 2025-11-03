package com.example.sbb.service;

import com.example.sbb.domain.User;
import com.example.sbb.dto.request.UserCreateRequest;
import com.example.sbb.dto.request.UserUpdateRequest;
import com.example.sbb.dto.response.UserResponse;
import com.example.sbb.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse createUser(UserCreateRequest request) {
        // 이메일 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다: " + request.getEmail());
        }

        // 사용자 생성
        User user = new User();
        user.setEmail(request.getEmail());
        user.setName(request.getName());
        // 비밀번호는 AuthService에서만 설정 (회원가입 API 사용)
        user.setPassword(""); // 임시값 (실제로는 사용 안 됨)
        user.setCreatedAt(OffsetDateTime.now());

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    public UserResponse findById(Long id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id));
        return toResponse(user);
    }

    public UserResponse findByEmail(String email) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + email));
        return toResponse(user);
    }

    public List<UserResponse> findAll() {
        return userRepository.findAll().stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse updateUser(Long id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id));

        // 이름만 수정 가능
        if (request.getName() != null) {
            user.setName(request.getName());
        }

        User saved = userRepository.save(user);
        return toResponse(saved);
    }

    @Transactional
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id);
        }
        userRepository.deleteById(id);
    }

    // 내부 메서드: Domain -> DTO 변환
    private UserResponse toResponse(User user) {
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setName(user.getName());
        response.setCreatedAt(user.getCreatedAt());
        return response;
    }

    // Service 간 사용을 위한 내부 메서드 (Entity 반환)
    public User findEntityById(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다: " + id));
    }
}

