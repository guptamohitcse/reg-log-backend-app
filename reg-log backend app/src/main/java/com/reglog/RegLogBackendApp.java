package com.reglog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.persistence.*;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import javax.crypto.SecretKey;

/**
 * =====================================================================================
 *  REG-LOG BACKEND APP  -  Spring Boot 3 + MySQL
 * =====================================================================================
 *  A single self contained file that holds the whole backend of the ecommerce app.
 *
 *  Layered architecture (as required by the assignment):
 *
 *      Frontend (React)  --JSON-->  CONTROLLER  -->  SERVICE  -->  REPOSITORY  -->  MySQL
 *                                          ^                                  |
 *                                          +---------- JSON {code,message} ----+
 *
 *  Resources exposed by this app:
 *      POST   /api/auth/signup            -> create account           (frontend: signup.jsx)
 *      POST   /api/auth/login             -> login + JWT access token (frontend: login.jsx)
 *      GET    /api/auth/me                -> profile of logged in user(frontend: home page)
 *      GET    /api/auth/users             -> list users  (ADMIN)
 *      PUT    /api/auth/users/{id}        -> update user (ADMIN or self)
 *      DELETE /api/auth/users/{id}        -> delete user (ADMIN)
 *      GET    /api/products               -> list / search products
 *      GET    /api/products/{id}          -> single product
 *      POST   /api/products               -> create product (ADMIN)
 *      PUT    /api/products/{id}          -> update product (ADMIN)
 *      DELETE /api/products/{id}          -> delete product (ADMIN)
 *      GET    /api/health                 -> health probe
 * =====================================================================================
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableJpaRepositories(considerNestedRepositories = true)
@EnableMethodSecurity
public class RegLogBackendApp {

    private static final Logger log = LoggerFactory.getLogger(RegLogBackendApp.class);

    public static void main(String[] args) {
        SpringApplication.run(RegLogBackendApp.class, args);
    }

    // =================================================================================
    // 1. RESPONSE WRAPPER  -  every response is  { code, message, data, timestamp }
    // =================================================================================
    public record ApiResponse<T>(int code, String message, T data, String timestamp) {

        public static <T> ApiResponse<T> ok(String message, T data) {
            return new ApiResponse<>(200, message, data, Instant.now().toString());
        }

        public static <T> ApiResponse<T> created(String message, T data) {
            return new ApiResponse<>(201, message, data, Instant.now().toString());
        }

        public static <T> ApiResponse<T> error(int code, String message, T data) {
            return new ApiResponse<>(code, message, data, Instant.now().toString());
        }
    }

    // =================================================================================
    // 2. REQUEST / RESPONSE DTOs (JSON payloads exchanged with the React frontend)
    // =================================================================================

    /** Payload sent by signup.jsx */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SignupRequest(
            @NotBlank(message = "Full name is required") String fullName,
            @NotBlank(message = "Gmail address is required") @Email(message = "Enter a valid gmail address") String gmail,
            @NotBlank(message = "Phone number is required") String phone,
            @NotBlank(message = "User id is required") @Size(min = 3, message = "User id must be at least 3 characters") String userId,
            @NotBlank(message = "Password is required") @Size(min = 6, message = "Password must be at least 6 characters") String password,
            String gender,
            String address,
            String city,
            String state,
            String pincode,
            String role
    ) {
    }

    /** Payload sent by login.jsx */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LoginRequest(
            @NotBlank(message = "Username is required") String username,
            @NotBlank(message = "Password is required") String password
    ) {
    }

    /** What the backend sends back after a successful login (contains the JWT access token) */
    public record LoginResponse(
            String token,
            String tokenType,
            long expiresIn,
            String username,
            String role,
            UserView user
    ) {
    }

    /** Safe user projection - the password hash is never sent to the frontend */
    public record UserView(
            Long id,
            String fullName,
            String gmail,
            String phone,
            String userId,
            String gender,
            String address,
            String city,
            String state,
            String pincode,
            String role,
            String createdAt
    ) {
    }

    /** Payload for product create / update */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductRequest(
            @NotBlank(message = "Product name is required") String name,
            String description,
            @NotNull(message = "Price is required") BigDecimal price,
            String category,
            String imageUrl,
            Integer stock,
            Double rating
    ) {
    }

    // =================================================================================
    // 3. ENTITIES (MySQL tables)
    // =================================================================================

    @Entity
    @Table(name = "users", uniqueConstraints = {
            @UniqueConstraint(name = "uk_users_userid", columnNames = "user_id"),
            @UniqueConstraint(name = "uk_users_gmail", columnNames = "gmail")
    })
    public static class User {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "full_name", nullable = false, length = 120)
        private String fullName;

        @Column(name = "gmail", nullable = false, length = 160)
        private String gmail;

        @Column(name = "phone", nullable = false, length = 25)
        private String phone;

        @Column(name = "user_id", nullable = false, length = 60)
        private String userId;

        @Column(name = "password_hash", nullable = false, length = 120)
        private String passwordHash;

        @Column(name = "gender", length = 20)
        private String gender;

        @Column(name = "address", length = 250)
        private String address;

        @Column(name = "city", length = 80)
        private String city;

        @Column(name = "state", length = 80)
        private String state;

        @Column(name = "pincode", length = 15)
        private String pincode;

        @Enumerated(EnumType.STRING)
        @Column(name = "role", nullable = false, length = 20)
        private Role role = Role.USER;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getFullName() {
            return fullName;
        }

        public void setFullName(String fullName) {
            this.fullName = fullName;
        }

        public String getGmail() {
            return gmail;
        }

        public void setGmail(String gmail) {
            this.gmail = gmail;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPasswordHash() {
            return passwordHash;
        }

        public void setPasswordHash(String passwordHash) {
            this.passwordHash = passwordHash;
        }

        public String getGender() {
            return gender;
        }

        public void setGender(String gender) {
            this.gender = gender;
        }

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getPincode() {
            return pincode;
        }

        public void setPincode(String pincode) {
            this.pincode = pincode;
        }

        public Role getRole() {
            return role;
        }

        public void setRole(Role role) {
            this.role = role;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public UserView toView() {
            return new UserView(id, fullName, gmail, phone, userId, gender, address, city, state, pincode,
                    role == null ? Role.USER.name() : role.name(), createdAt == null ? null : createdAt.toString());
        }
    }

    @Entity
    @Table(name = "products")
    public static class Product {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(nullable = false, length = 160)
        private String name;

        @Column(columnDefinition = "TEXT")
        private String description;

        @Column(nullable = false, precision = 12, scale = 2)
        private BigDecimal price;

        @Column(length = 60)
        private String category;

        @Column(length = 500)
        private String imageUrl;

        @Column(nullable = false)
        private Integer stock = 0;

        @Column(nullable = false)
        private Double rating = 0.0;

        @Column(name = "created_at", nullable = false)
        private Instant createdAt = Instant.now();

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public void setPrice(BigDecimal price) {
            this.price = price;
        }

        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public Integer getStock() {
            return stock;
        }

        public void setStock(Integer stock) {
            this.stock = stock;
        }

        public Double getRating() {
            return rating;
        }

        public void setRating(Double rating) {
            this.rating = rating;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }
    }

    public enum Role {
        USER, ADMIN
    }

    // =================================================================================
    // 4. REPOSITORY LAYER - the only layer that talks to MySQL
    // =================================================================================

    @Repository
    public interface UserRepository extends JpaRepository<User, Long> {

        Optional<User> findByUserIdIgnoreCase(String userId);

        Optional<User> findByGmailIgnoreCase(String gmail);

        boolean existsByUserIdIgnoreCase(String userId);

        boolean existsByGmailIgnoreCase(String gmail);

        List<User> findAllByOrderByCreatedAtDesc();
    }

    @Repository
    public interface ProductRepository extends JpaRepository<Product, Long> {

        List<Product> findAllByOrderByIdAsc();

        List<Product> findByCategoryIgnoreCase(String category);

        List<Product> findByNameContainingIgnoreCase(String name);

        List<Product> findByCategoryIgnoreCaseAndNameContainingIgnoreCase(String category, String name);
    }

    // =================================================================================
    // 5. SERVICE LAYER - validation, business rules, verification
    // =================================================================================

    @Service
    public static class UserService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;

        @Autowired
        public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
            this.userRepository = userRepository;
            this.passwordEncoder = passwordEncoder;
        }

        /**
         * SIGN UP - validates the payload, hashes the password and asks the repository
         * to persist the new user into MySQL.
         */
        public UserView register(SignupRequest request) {
            String userId = request.userId().trim();
            String gmail = request.gmail().trim().toLowerCase(Locale.ROOT);

            if (userRepository.existsByUserIdIgnoreCase(userId)) {
                throw new ApiException(409, "User id '" + userId + "' is already taken");
            }
            if (userRepository.existsByGmailIgnoreCase(gmail)) {
                throw new ApiException(409, "An account with this gmail already exists");
            }
            if (!gmail.endsWith("@gmail.com")) {
                throw new ApiException(400, "Please use a valid @gmail.com address");
            }

            User user = new User();
            user.setFullName(request.fullName().trim());
            user.setGmail(gmail);
            user.setPhone(request.phone().trim());
            user.setUserId(userId);
            user.setPasswordHash(passwordEncoder.encode(request.password()));
            user.setGender(blankToNull(request.gender()));
            user.setAddress(blankToNull(request.address()));
            user.setCity(blankToNull(request.city()));
            user.setState(blankToNull(request.state()));
            user.setPincode(blankToNull(request.pincode()));

            // The very first account of the store becomes the ADMIN, everyone else is a USER.
            boolean firstEverUser = userRepository.count() == 0;
            Role requested = parseRole(request.role());
            user.setRole(firstEverUser ? Role.ADMIN : (requested == null ? Role.USER : requested));
            user.setCreatedAt(Instant.now());

            User saved = userRepository.save(user);
            log.info("User registered: {} (role={})", saved.getUserId(), saved.getRole());
            return saved.toView();
        }

        /**
         * LOGIN - fetches the user from the repository by username and verifies the password.
         * The JWT itself is created by the controller, exactly as the assignment asks.
         */
        public User verifyCredentials(LoginRequest request) {
            String username = request.username() == null ? "" : request.username().trim();
            User user = userRepository.findByUserIdIgnoreCase(username)
                    .or(() -> userRepository.findByGmailIgnoreCase(username))
                    .orElseThrow(() -> new ApiException(401, "Invalid username or password"));

            if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                throw new ApiException(401, "Invalid username or password");
            }
            log.info("User authenticated: {}", user.getUserId());
            return user;
        }

        public User findByUserId(String userId) {
            return userRepository.findByUserIdIgnoreCase(userId)
                    .orElseThrow(() -> new ApiException(404, "User '" + userId + "' not found"));
        }

        public UserView update(Long id, SignupRequest request) {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new ApiException(404, "User not found"));

            if (request.fullName() != null && !request.fullName().isBlank()) {
                user.setFullName(request.fullName().trim());
            }
            if (request.gmail() != null && !request.gmail().isBlank()) {
                String gmail = request.gmail().trim().toLowerCase(Locale.ROOT);
                userRepository.findByGmailIgnoreCase(gmail)
                        .filter(other -> !other.getId().equals(id))
                        .ifPresent(other -> {
                            throw new ApiException(409, "That gmail is already used by another account");
                        });
                user.setGmail(gmail);
            }
            if (request.phone() != null && !request.phone().isBlank()) {
                user.setPhone(request.phone().trim());
            }
            if (request.password() != null && !request.password().isBlank()) {
                user.setPasswordHash(passwordEncoder.encode(request.password()));
            }
            if (request.address() != null) {
                user.setAddress(blankToNull(request.address()));
            }
            if (request.city() != null) {
                user.setCity(blankToNull(request.city()));
            }
            if (request.state() != null) {
                user.setState(blankToNull(request.state()));
            }
            if (request.pincode() != null) {
                user.setPincode(blankToNull(request.pincode()));
            }
            if (request.gender() != null) {
                user.setGender(blankToNull(request.gender()));
            }

            User saved = userRepository.save(user);
            return saved.toView();
        }

        public void delete(Long id) {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new ApiException(404, "User not found"));
            userRepository.delete(user);
            log.info("User deleted: {}", user.getUserId());
        }

        public List<UserView> list() {
            return userRepository.findAllByOrderByCreatedAtDesc().stream()
                    .map(User::toView)
                    .collect(Collectors.toList());
        }

        private static Role parseRole(String role) {
            if (role == null || role.isBlank()) {
                return null;
            }
            try {
                return Role.valueOf(role.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ApiException(400, "Invalid role '" + role + "'. Allowed values: USER, ADMIN");
            }
        }

        private static String blankToNull(String value) {
            return (value == null || value.isBlank()) ? null : value.trim();
        }
    }

    @Service
    public static class ProductService {

        private final ProductRepository productRepository;

        @Autowired
        public ProductService(ProductRepository productRepository) {
            this.productRepository = productRepository;
        }

        public List<Product> findAll(String category, String search) {
            boolean hasCategory = category != null && !category.isBlank() && !"All".equalsIgnoreCase(category);
            boolean hasSearch = search != null && !search.isBlank();

            if (hasCategory && hasSearch) {
                return productRepository.findByCategoryIgnoreCaseAndNameContainingIgnoreCase(category.trim(), search.trim());
            }
            if (hasCategory) {
                return productRepository.findByCategoryIgnoreCase(category.trim());
            }
            if (hasSearch) {
                return productRepository.findByNameContainingIgnoreCase(search.trim());
            }
            return productRepository.findAllByOrderByIdAsc();
        }

        public Product findById(Long id) {
            return productRepository.findById(id)
                    .orElseThrow(() -> new ApiException(404, "Product " + id + " not found"));
        }

        public Product create(ProductRequest request) {
            Product product = new Product();
            apply(product, request);
            Product saved = productRepository.save(product);
            log.info("Product created: {}", saved.getName());
            return saved;
        }

        public Product update(Long id, ProductRequest request) {
            Product product = findById(id);
            apply(product, request);
            Product saved = productRepository.save(product);
            log.info("Product updated: {}", saved.getName());
            return saved;
        }

        public void delete(Long id) {
            Product product = findById(id);
            productRepository.delete(product);
            log.info("Product deleted: {}", product.getName());
        }

        public List<String> categories() {
            return productRepository.findAll().stream()
                    .map(Product::getCategory)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());
        }

        private static void apply(Product product, ProductRequest request) {
            if (request.name() != null && !request.name().isBlank()) {
                product.setName(request.name().trim());
            }
            if (request.description() != null) {
                product.setDescription(request.description());
            }
            if (request.price() != null) {
                if (request.price().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new ApiException(400, "Price must be greater than 0");
                }
                product.setPrice(request.price());
            }
            if (request.category() != null && !request.category().isBlank()) {
                product.setCategory(request.category().trim());
            }
            if (request.imageUrl() != null) {
                product.setImageUrl(request.imageUrl().isBlank() ? null : request.imageUrl().trim());
            }
            if (request.stock() != null) {
                if (request.stock() < 0) {
                    throw new ApiException(400, "Stock cannot be negative");
                }
                product.setStock(request.stock());
            }
            if (request.rating() != null) {
                product.setRating(Math.max(0, Math.min(5, request.rating())));
            }
        }
    }

    // =================================================================================
    // 6. CONTROLLER LAYER - receives the JSON payload, talks to the service,
    //    creates the JWT access token on a successful login and answers with JSON.
    // =================================================================================

    @RestController
    @RequestMapping("/api/auth")
    public static class AuthController {

        private final UserService userService;
        private final JwtUtil jwtUtil;

        @Autowired
        public AuthController(UserService userService, JwtUtil jwtUtil) {
            this.userService = userService;
            this.jwtUtil = jwtUtil;
        }

        /** POST /api/auth/signup  -  called by signup.jsx */
        @PostMapping("/signup")
        public ResponseEntity<ApiResponse<UserView>> signup(@Valid @RequestBody SignupRequest request) {
            UserView created = userService.register(request);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.created("Account created successfully", created));
        }

        /** POST /api/auth/login  -  called by login.jsx, returns the JWT access token */
        @PostMapping("/login")
        public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
            // service -> repository -> back : user fetched from MySQL and verified
            User user = userService.verifyCredentials(request);

            // controller creates the signed JWT (role + username in the claims)
            String role = user.getRole() == null ? Role.USER.name() : user.getRole().name();
            String token = jwtUtil.generateToken(user.getUserId(), role);

            LoginResponse payload = new LoginResponse(
                    token,
                    "Bearer",
                    jwtUtil.getExpirationMillis(),
                    user.getUserId(),
                    role,
                    user.toView()
            );
            return ResponseEntity.ok(ApiResponse.ok("Login successful", payload));
        }

        /** GET /api/auth/me - profile of the logged in user (used by the home page) */
        @GetMapping("/me")
        public ResponseEntity<ApiResponse<UserView>> me(HttpServletRequest request) {
            String username = (String) request.getAttribute("reglog.username");
            if (username == null) {
                throw new ApiException(401, "Access token missing or invalid");
            }
            User user = userService.findByUserId(username);
            return ResponseEntity.ok(ApiResponse.ok("Profile fetched", user.toView()));
        }

        /** GET /api/auth/users - list all users (ADMIN only) */
        @GetMapping("/users")
        public ResponseEntity<ApiResponse<List<UserView>>> users() {
            return ResponseEntity.ok(ApiResponse.ok("Users fetched", userService.list()));
        }

        /** PUT /api/auth/users/{id} - update a user (ADMIN only) */
        @PutMapping("/users/{id}")
        public ResponseEntity<ApiResponse<UserView>> update(@PathVariable Long id,
                                                           @Valid @RequestBody SignupRequest request) {
            return ResponseEntity.ok(ApiResponse.ok("User updated", userService.update(id, request)));
        }

        /** DELETE /api/auth/users/{id} - delete a user (ADMIN only) */
        @DeleteMapping("/users/{id}")
        public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@PathVariable Long id) {
            userService.delete(id);
            return ResponseEntity.ok(ApiResponse.ok("User deleted", Map.of("id", id)));
        }
    }

    @RestController
    @RequestMapping("/api/products")
    public static class ProductController {

        private final ProductService productService;

        @Autowired
        public ProductController(ProductService productService) {
            this.productService = productService;
        }

        /** GET /api/products?category=&search= - used by the home page */
        @GetMapping
        public ResponseEntity<ApiResponse<List<Product>>> list(@RequestParam(required = false) String category,
                                                             @RequestParam(required = false) String search) {
            return ResponseEntity.ok(ApiResponse.ok("Products fetched", productService.findAll(category, search)));
        }

        @GetMapping("/categories")
        public ResponseEntity<ApiResponse<List<String>>> categories() {
            return ResponseEntity.ok(ApiResponse.ok("Categories fetched", productService.categories()));
        }

        @GetMapping("/{id}")
        public ResponseEntity<ApiResponse<Product>> get(@PathVariable Long id) {
            return ResponseEntity.ok(ApiResponse.ok("Product fetched", productService.findById(id)));
        }

        /** POST /api/products - create a product (ADMIN only) */
        @PostMapping
        public ResponseEntity<ApiResponse<Product>> create(@Valid @RequestBody ProductRequest request) {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(ApiResponse.created("Product created", productService.create(request)));
        }

        /** PUT /api/products/{id} - update a product (ADMIN only) */
        @PutMapping("/{id}")
        public ResponseEntity<ApiResponse<Product>> update(@PathVariable Long id,
                                                          @Valid @RequestBody ProductRequest request) {
            return ResponseEntity.ok(ApiResponse.ok("Product updated", productService.update(id, request)));
        }

        /** DELETE /api/products/{id} - delete a product (ADMIN only) */
        @DeleteMapping("/{id}")
        public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@PathVariable Long id) {
            productService.delete(id);
            return ResponseEntity.ok(ApiResponse.ok("Product deleted", Map.of("id", id)));
        }
    }

    @RestController
    @RequestMapping("/api")
    public static class HealthController {

        @GetMapping("/health")
        public ResponseEntity<ApiResponse<Map<String, String>>> health() {
            return ResponseEntity.ok(ApiResponse.ok("Backend is up and running",
                    Map.of("status", "UP", "service", "reg-log-backend-app")));
        }
    }

    // =================================================================================
    // 7. JWT UTILITY + SECURITY FILTER
    // =================================================================================

    @Component
    public static class JwtUtil {

        private final String secret;
        private final String issuer;
        private final long expirationMillis;
        private final SecretKey key;

        @Autowired
        public JwtUtil(
                @org.springframework.beans.factory.annotation.Value("${app.jwt.secret}") String secret,
                @org.springframework.beans.factory.annotation.Value("${app.jwt.issuer}") String issuer,
                @org.springframework.beans.factory.annotation.Value("${app.jwt.expiration-ms}") long expirationMillis) {
            this.secret = secret;
            this.issuer = issuer;
            this.expirationMillis = expirationMillis;
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }

        /** Creates a signed JWT that carries the role + username. */
        public String generateToken(String username, String role) {
            Instant now = Instant.now();
            return Jwts.builder()
                    .issuer(issuer)
                    .subject(username)
                    .claim("role", role)
                    .claim("username", username)
                    .issuedAt(Date.from(now))
                    .expiration(Date.from(now.plusMillis(expirationMillis)))
                    .signWith(key)
                    .compact();
        }

        /** Reads + verifies the token. Returns null when the token is missing or invalid. */
        public Claims parse(String token) {
            try {
                return Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (Exception ex) {
                return null;
            }
        }

        public long getExpirationMillis() {
            return expirationMillis;
        }
    }

    /** Reads the "Authorization: Bearer <token>" header and populates the security context. */
    public static class JwtAuthFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;

        public JwtAuthFilter(JwtUtil jwtUtil) {
            this.jwtUtil = jwtUtil;
        }

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                String token = header.substring(7).trim();
                Claims claims = jwtUtil.parse(token);
                if (claims != null) {
                    String username = claims.getSubject();
                    String role = claims.get("role", String.class);
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + (role == null ? "USER" : role)));
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(username, null, authorities);
                    authentication.setDetails(new org.springframework.security.web.authentication.WebAuthenticationDetailsSource()
                            .buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    request.setAttribute("reglog.username", username);
                    request.setAttribute("reglog.role", role);
                }
            }
            filterChain.doFilter(request, response);
        }
    }

    // =================================================================================
    // 8. SECURITY CONFIGURATION
    // =================================================================================

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtUtil jwtUtil) {
        return new JwtAuthFilter(jwtUtil);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter,
                                                    CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ---- public endpoints ----
                        .requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health", "/api/products", "/api/products/*",
                                "/api/products/categories").permitAll()
                        // ---- admin only endpoints ----
                        .requestMatchers("/api/auth/users/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/products").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/products/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/products/**").hasRole("ADMIN")
                        // ---- everything else needs a valid JWT ----
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"code\":401,\"message\":\"Access token missing or invalid\",\"data\":null}");
                        })
                        .accessDeniedHandler((request, response, deniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType("application/json");
                            response.getWriter().write(
                                    "{\"code\":403,\"message\":\"You do not have permission to perform this action\",\"data\":null}");
                        }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${app.cors.allowed-origins}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    // =================================================================================
    // 9. GLOBAL EXCEPTION HANDLER - keeps every error in the same {code,message} shape
    // =================================================================================

    public static class ApiException extends RuntimeException {
        private final int code;

        public ApiException(int code, String message) {
            super(message);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    @RestControllerAdvice
    public static class GlobalExceptionHandler {

        @ExceptionHandler(ApiException.class)
        public ResponseEntity<ApiResponse<Object>> handleApi(ApiException ex) {
            return ResponseEntity.status(ex.getCode())
                    .body(ApiResponse.error(ex.getCode(), ex.getMessage(), null));
        }

        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ApiResponse<Object>> handleValidation(MethodArgumentNotValidException ex) {
            String message = ex.getBindingResult().getFieldErrors().stream()
                    .map(error -> error.getField() + ": " + error.getDefaultMessage())
                    .findFirst()
                    .orElse("Validation failed");
            return ResponseEntity.badRequest().body(ApiResponse.error(400, message, null));
        }

        @ExceptionHandler(Exception.class)
        public ResponseEntity<ApiResponse<Object>> handleOther(Exception ex) {
            log.error("Unhandled error", ex);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error(500, "Something went wrong on the server: " + ex.getMessage(), null));
        }
    }

    // =================================================================================
    // 10. DATA SEEDER - creates the demo products and a demo admin account
    // =================================================================================

    @Component
    public static class DataSeeder implements CommandLineRunner {

        private final ProductRepository productRepository;
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;

        @Autowired
        public DataSeeder(ProductRepository productRepository, UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
            this.productRepository = productRepository;
            this.userRepository = userRepository;
            this.passwordEncoder = passwordEncoder;
        }

        @Override
        public void run(String... args) {
            if (productRepository.count() == 0) {
                productRepository.saveAll(List.of(
                        product("Aero Running Shoes", "Lightweight everyday running shoes with breathable mesh upper.",
                                new BigDecimal("2499.00"), "Footwear",
                                "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&q=80", 42, 4.6),
                        product("Minimalist Analog Watch", "Slim 38mm case, stainless steel strap, quartz movement.",
                                new BigDecimal("3499.00"), "Accessories",
                                "https://images.unsplash.com/photo-1523170335258-f5ed11844a49?w=600&q=80", 25, 4.4),
                        product("Cotton Crew Neck Tee", "Premium 180 GSM combed cotton, regular fit.",
                                new BigDecimal("799.00"), "Apparel",
                                "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=600&q=80", 120, 4.2),
                        product("Stoneware Coffee Mug", "Matte glazed 350ml mug, dishwasher safe.",
                                new BigDecimal("499.00"), "Home",
                                "https://images.unsplash.com/photo-1514228742587-6b1558fcca3d?w=600&q=80", 80, 4.5),
                        product("Noise Cancelling Headphones", "Over-ear headphones with 30h battery and ANC.",
                                new BigDecimal("7999.00"), "Electronics",
                                "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=600&q=80", 18, 4.8),
                        product("Leather Card Holder", "Full grain tan leather, six card slots.",
                                new BigDecimal("1299.00"), "Accessories",
                                "https://images.unsplash.com/photo-1627123424574-724758594e93?w=600&q=80", 55, 4.3),
                        product("Scented Soy Candle", "Cedar and amber 220g candle, 40h burn time.",
                                new BigDecimal("899.00"), "Home",
                                "https://images.unsplash.com/photo-1602874801006-e26d3c8f1a54?w=600&q=80", 64, 4.1),
                        product("Canvas Backpack", "Water resistant 22L backpack with a padded laptop sleeve.",
                                new BigDecimal("2199.00"), "Accessories",
                                "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&q=80", 37, 4.7)
                ));
                log.info("Seeded {} demo products", productRepository.count());
            }

            // demo admin account so the reviewer can log in with ADMIN rights
            if (!userRepository.existsByUserIdIgnoreCase("admin")) {
                User admin = new User();
                admin.setFullName("Store Admin");
                admin.setGmail("admin@reglog.com");
                admin.setPhone("9000000000");
                admin.setUserId("admin");
                admin.setPasswordHash(passwordEncoder.encode("admin@123"));
                admin.setRole(Role.ADMIN);
                admin.setCity("Bengaluru");
                admin.setState("Karnataka");
                admin.setPincode("560001");
                admin.setAddress("12 MG Road");
                admin.setGender("Other");
                userRepository.save(admin);
                log.info("Seeded demo admin account (admin / admin@123)");
            }
        }

        private static Product product(String name, String description, BigDecimal price, String category,
                                       String imageUrl, int stock, double rating) {
            Product product = new Product();
            product.setName(name);
            product.setDescription(description);
            product.setPrice(price);
            product.setCategory(category);
            product.setImageUrl(imageUrl);
            product.setStock(stock);
            product.setRating(rating);
            return product;
        }
    }
}
