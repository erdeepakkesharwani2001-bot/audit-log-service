package com.schwab.audit.service;

import com.schwab.audit.dto.request.RegisterRequest;
import com.schwab.audit.dto.response.RegisterResponse;
import com.schwab.audit.entity.User;
import com.schwab.audit.entity.enums.UserRole;
import com.schwab.audit.exception.BadRequestException;
import com.schwab.audit.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    // ============================================================
    // SUCCESS
    // ============================================================

    @Test
    void registerUser_shouldRegisterUserSuccessfully() {

        RegisterRequest request = new RegisterRequest();
        request.setUsername("newadmin");
        request.setPassword("Password@123");
        request.setRole("ADMIN");

        when(userRepository.existsByUsername("newadmin"))
                .thenReturn(false);

        when(passwordEncoder.encode("Password@123"))
                .thenReturn("$2a$12$encodedPassword");

        User savedUser = User.builder()
                .id(1L)
                .username("newadmin")
                .password("$2a$12$encodedPassword")
                .role(UserRole.ADMIN)
                .build();

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        RegisterResponse response =
                userService.registerUser(request);

        assertNotNull(response);

        assertEquals(1L, response.getUserId());
        assertEquals("newadmin", response.getUsername());
        assertEquals("ADMIN", response.getRole());
        assertEquals(
                "User registered successfully",
                response.getMessage()
        );

        verify(userRepository)
                .existsByUsername("newadmin");

        verify(passwordEncoder)
                .encode("Password@123");

        verify(userRepository)
                .save(any(User.class));
    }

    // ============================================================
    // SUCCESS - AUDIT_WRITER
    // ============================================================

    @Test
    void registerUser_shouldRegisterAuditWriter() {

        RegisterRequest request = new RegisterRequest();
        request.setUsername("writer");
        request.setPassword("Writer@123");
        request.setRole("AUDIT_WRITER");

        when(userRepository.existsByUsername("writer"))
                .thenReturn(false);

        when(passwordEncoder.encode("Writer@123"))
                .thenReturn("encoded-password");

        User savedUser = User.builder()
                .id(2L)
                .username("writer")
                .password("encoded-password")
                .role(UserRole.AUDIT_WRITER)
                .build();

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        RegisterResponse response =
                userService.registerUser(request);

        assertNotNull(response);
        assertEquals(2L, response.getUserId());
        assertEquals("writer", response.getUsername());
        assertEquals("AUDIT_WRITER", response.getRole());
        assertEquals(
                "User registered successfully",
                response.getMessage()
        );

        verify(userRepository).save(any(User.class));
    }

    // ============================================================
    // SUCCESS - AUDITOR
    // ============================================================

    @Test
    void registerUser_shouldRegisterAuditor() {

        RegisterRequest request = new RegisterRequest();
        request.setUsername("auditor");
        request.setPassword("Auditor@123");
        request.setRole("AUDITOR");

        when(userRepository.existsByUsername("auditor"))
                .thenReturn(false);

        when(passwordEncoder.encode("Auditor@123"))
                .thenReturn("encoded-password");

        User savedUser = User.builder()
                .id(3L)
                .username("auditor")
                .password("encoded-password")
                .role(UserRole.AUDITOR)
                .build();

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        RegisterResponse response =
                userService.registerUser(request);

        assertNotNull(response);
        assertEquals(3L, response.getUserId());
        assertEquals("auditor", response.getUsername());
        assertEquals("AUDITOR", response.getRole());

        verify(userRepository).save(any(User.class));
    }

    // ============================================================
    // DUPLICATE USERNAME
    // ============================================================

    @Test
    void registerUser_shouldThrowExceptionWhenUsernameAlreadyExists() {

        RegisterRequest request = new RegisterRequest();
        request.setUsername("existing");
        request.setPassword("Password@123");
        request.setRole("ADMIN");

        when(userRepository.existsByUsername("existing"))
                .thenReturn(true);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userService.registerUser(request)
        );

        assertEquals(
                "Username 'existing' is already taken. Please choose a different username.",
                exception.getMessage()
        );

        verify(userRepository)
                .existsByUsername("existing");

        verify(userRepository, never())
                .save(any(User.class));

        verify(passwordEncoder, never())
                .encode(anyString());
    }

    // ============================================================
    // INVALID ROLE
    // ============================================================

    @Test
    void registerUser_shouldThrowExceptionForInvalidRole() {

        RegisterRequest request = new RegisterRequest();
        request.setUsername("testuser");
        request.setPassword("Password@123");
        request.setRole("INVALID_ROLE");

        when(userRepository.existsByUsername("testuser"))
                .thenReturn(false);

        BadRequestException exception = assertThrows(
                BadRequestException.class,
                () -> userService.registerUser(request)
        );

        assertEquals(
                "Invalid role: INVALID_ROLE. Valid roles are: AUDIT_WRITER, AUDITOR, ADMIN",
                exception.getMessage()
        );

        verify(userRepository)
                .existsByUsername("testuser");

        verify(userRepository, never())
                .save(any(User.class));

        verify(passwordEncoder, never())
                .encode(anyString());
    }

    // ============================================================
    // VERIFY PASSWORD IS ENCODED
    // ============================================================

    @Test
    void registerUser_shouldEncodePasswordBeforeSaving() {

        RegisterRequest request = new RegisterRequest();
        request.setUsername("secureuser");
        request.setPassword("MyPassword123");
        request.setRole("ADMIN");

        when(userRepository.existsByUsername("secureuser"))
                .thenReturn(false);

        when(passwordEncoder.encode("MyPassword123"))
                .thenReturn("BCryptEncodedPassword");

        User savedUser = User.builder()
                .id(10L)
                .username("secureuser")
                .password("BCryptEncodedPassword")
                .role(UserRole.ADMIN)
                .build();

        when(userRepository.save(any(User.class)))
                .thenReturn(savedUser);

        userService.registerUser(request);

        ArgumentCaptor<User> userCaptor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(userCaptor.capture());

        User capturedUser = userCaptor.getValue();

        assertEquals(
                "secureuser",
                capturedUser.getUsername()
        );

        assertEquals(
                "BCryptEncodedPassword",
                capturedUser.getPassword()
        );

        assertEquals(
                UserRole.ADMIN,
                capturedUser.getRole()
        );

        verify(passwordEncoder)
                .encode("MyPassword123");
    }

    // ============================================================
    // VERIFY PASSWORD IS NOT STORED AS PLAIN TEXT
    // ============================================================

    @Test
    void registerUser_shouldNotSavePlainTextPassword() {

        RegisterRequest request = new RegisterRequest();
        request.setUsername("secureuser");
        request.setPassword("PlainPassword");
        request.setRole("ADMIN");

        when(userRepository.existsByUsername("secureuser"))
                .thenReturn(false);

        when(passwordEncoder.encode("PlainPassword"))
                .thenReturn("EncodedPassword");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.registerUser(request);

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        User user = captor.getValue();

        assertNotEquals(
                "PlainPassword",
                user.getPassword()
        );

        assertEquals(
                "EncodedPassword",
                user.getPassword()
        );
    }

    // ============================================================
    // VERIFY CREATED AT
    // ============================================================

    @Test
    void registerUser_shouldSetCreatedAt() {

        RegisterRequest request = new RegisterRequest();
        request.setUsername("timeuser");
        request.setPassword("Password123");
        request.setRole("ADMIN");

        when(userRepository.existsByUsername("timeuser"))
                .thenReturn(false);

        when(passwordEncoder.encode("Password123"))
                .thenReturn("encoded");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.registerUser(request);

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository).save(captor.capture());

        User user = captor.getValue();

        assertNotNull(user.getCreatedAt());
    }
}