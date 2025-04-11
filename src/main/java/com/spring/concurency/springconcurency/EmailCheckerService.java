package com.spring.concurency.springconcurency;


import org.hibernate.query.sqm.BinaryArithmeticOperator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongBinaryOperator;

@Service
public class EmailCheckerService {

    private static final String EMAIL_CACHE_KEY = "registered_emails";
    private static final String EMAIL_HASH_CACHE_KEY = "registered_emails_hash";
    private static final Logger logger = LoggerFactory.getLogger(EmailCheckerService.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private UserRepository userRepository;

    private final int BATCH_SIZE = 5000;
    private final int NUM_USERS_TO_GENERATE = 100_000_000;

    public boolean isEmailRegistered(String email) {
        if (!StringUtils.hasLength(email)) {
            return false;
        }
        var emailHash = hashEmailToOffset(email);
        Boolean isSet = redisTemplate.opsForValue().getBit(EMAIL_HASH_CACHE_KEY, emailHash);

        if (Boolean.TRUE.equals(isSet)) {
            var value = redisTemplate.opsForValue().getBit(EMAIL_HASH_CACHE_KEY, emailHash).toString();
            logger.info("Email existed in Redis: " + value);
            return true;
        }
        return false;
    }

    @Transactional
    public boolean registerEmail(String email) {
        if (!StringUtils.hasLength(email) || isEmailRegistered(email)) {
            return false;
        }
        var emailHash = hashEmailToOffset(email);
        redisTemplate.opsForValue().setBit(EMAIL_HASH_CACHE_KEY, emailHash, true);
        try {
            userRepository.save(new UserEntity(email, emailHash));
            return true;
        } catch (DataIntegrityViolationException e) {
            redisTemplate.opsForValue().setBit(EMAIL_HASH_CACHE_KEY, emailHash, false);
            return false;
        }
    }

    public String dumpUsersToPostgres() {
        long startTime = System.currentTimeMillis();
        ExecutorService executorService = Executors.newWorkStealingPool(Runtime.getRuntime().availableProcessors());

        List<UserEntity> batch = new ArrayList<>(BATCH_SIZE);
        for (int i = 0; i < NUM_USERS_TO_GENERATE; i++) {
            String email = UUID.randomUUID().toString().replace("-", "") + "@example.com";
            var emailHash = hashEmailToOffset(email);
            batch.add(new UserEntity(email, emailHash));

            if (batch.size() == BATCH_SIZE) {
                List<UserEntity> finalBatch = new ArrayList<>(batch);
                executorService.submit(() -> {
                    try {
                        userRepository.saveAll(finalBatch);
                        logger.info("Dumped batch of {} users", finalBatch.size());
                    } catch (Exception e) {
                        logger.error("Error dumping batch: {}", e.getMessage());
                    }
                });
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            List<UserEntity> finalBatch = new ArrayList<>(batch);
            executorService.submit(() -> {
                try {
                    userRepository.saveAll(finalBatch);
                    logger.info("Dumped final batch of {} users", finalBatch.size());
                } catch (Exception e) {
                    logger.error("Error dumping final batch: {}", e.getMessage());
                }
            });
        }

        executorService.shutdown();
        try {
            executorService.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long endTime = System.currentTimeMillis();
        return String.format("Dumped %d users to PostgreSQL in %d ms", NUM_USERS_TO_GENERATE, (endTime - startTime));
    }

    public String loadUsersToRedis() {
        long startTime = System.currentTimeMillis();
        AtomicLong count = new AtomicLong();
        AtomicInteger counter = new AtomicInteger(0); // Bắt đầu từ trang 0
        int pageSize = 1000000;
        try {
            while (true) {
                Page<UserEntity> page = userRepository.findAll(PageRequest.of(counter.get(), pageSize, Sort.by(Sort.Direction.ASC, "id")));
                if (page.isEmpty()) {
                    break; // Không còn dữ liệu
                }
                page.getContent().parallelStream().forEach(user -> {
                    Integer offset = user.getEmailHash().intValue(); // Lấy emailHash và chuyển sang Integer
                    redisTemplate.opsForValue().setBit(EMAIL_HASH_CACHE_KEY, offset, true); // Sử dụng EMAIL_HASH_CACHE_KEY
                });
                counter.incrementAndGet();
                count.accumulateAndGet((long) pageSize, (current, update) -> current + update);
                logger.info("Loaded {} email hashes (int) to Redis", count.get());
            }
        } catch (Exception e) {
            logger.error("Error loading email hashes (int) to Redis: {}", e.getMessage());
            e.printStackTrace();
            return "Error loading email hashes (int) to Redis";
        }

        long endTime = System.currentTimeMillis();
        return String.format("Loaded %d email hashes (int) to Redis in %d ms", count.get(), (endTime - startTime));
    }

    public Integer hashEmailToOffset(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(email.getBytes(StandardCharsets.UTF_8));
            int offset = 0;
            // Lấy 4 byte đầu tiên của hash và chuyển thành int
            for (int i = 0; i < Math.min(4, hashBytes.length); i++) {
                offset |= (hashBytes[i] & 0xFF) << (i * 8);
            }
            return Math.abs(offset);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return 0; // Trả về 0 hoặc một giá trị mặc định khác
        }
    }
}