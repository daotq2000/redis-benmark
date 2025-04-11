package com.spring.concurency.springconcurency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class UserService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Map<String, Lock> emailLocks = new ConcurrentHashMap<>(); // Map để quản lý khóa theo email
    private final UserRepository userRepository;
    private final EmailCheckerService emailCheckerService;
    private final String EMAIL_USER_PROFILE = "profile_email_";
    public UserService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, UserRepository userRepository, EmailCheckerService emailCheckerService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.emailCheckerService = emailCheckerService;
    }

    public UserEntity findUser(String email) {
        var key =EMAIL_USER_PROFILE+email;
        String cachedUserJson = redisTemplate.opsForValue().get(key);
        if (cachedUserJson != null) {
            try {
                return objectMapper.readValue(cachedUserJson, UserEntity.class);
            } catch (Exception e) {
                // Xử lý lỗi deserialization, có thể log và thử truy vấn database
                throw new RuntimeException(e);
            }
        }

        // Cache miss, cần truy vấn database
        return findUserFromDatabaseWithLock(email);
    }

    private UserEntity findUserFromDatabaseWithLock(String email) {
        var key =EMAIL_USER_PROFILE+email;  // Lấy hoặc tạo khóa cho email này
        Lock lock = emailLocks.computeIfAbsent(email, k -> new ReentrantLock());

        try {
            // Chỉ một thread được phép thực hiện truy vấn database cho email này tại một thời điểm
            if (lock.tryLock(1, TimeUnit.SECONDS)) { // Thử khóa với timeout để tránh deadlock
                try {
                    // Kiểm tra lại cache sau khi có khóa (double-check locking)
                    String cachedUserJson = redisTemplate.opsForValue().get(key);
                    if (cachedUserJson != null) {
                        return objectMapper.readValue(cachedUserJson, UserEntity.class);
                    }

                    // Thực hiện truy vấn database
                    UserEntity userFromDb = findUserFromDatabase(email);

                    if (userFromDb != null) {
                        // Cache dữ liệu vào Redis
                        redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(userFromDb), 1, TimeUnit.HOURS); // Ví dụ thời gian sống là 1 giờ
                        return userFromDb;
                    }
                    return null; // Không tìm thấy trong database
                } finally {
                    lock.unlock(); // Giải phóng khóa
                }
            } else {
                // Không lấy được khóa trong thời gian chờ, có nghĩa là một thread khác đang truy vấn database
                // Chờ một khoảng thời gian ngắn và thử lại đọc từ cache
                TimeUnit.MILLISECONDS.sleep(100);
                return findUser(email); // Gọi lại để kiểm tra cache
            }
        } catch (InterruptedException | JsonProcessingException e) {
            Thread.currentThread().interrupt();
            // Xử lý ngoại lệ
            return null;
        }
    }

    private UserEntity findUserFromDatabase(String email) {
        // Logic truy vấn database
        Optional<UserEntity> userFromDb = userRepository.findByUserEmail(email);
        if (userFromDb.isPresent()) {
            try {
                redisTemplate.opsForValue().set(email, objectMapper.writeValueAsString(userFromDb.get()), 1, TimeUnit.HOURS);
                return userFromDb.get();
            } catch (Exception e) {
                // Xử lý lỗi khi set cache
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException("User Not Found");
    }

 
}