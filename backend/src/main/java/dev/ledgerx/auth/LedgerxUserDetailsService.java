package dev.ledgerx.auth;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads a principal by email. Bearer authentication does not use this — the
 * filter builds its authentication from token claims — but defining the bean
 * makes Spring Boot's UserDetailsServiceAutoConfiguration back off, which is
 * what stops a random password being generated and logged on every boot.
 */
@Service
public class LedgerxUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public LedgerxUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(AuthService.normalizeEmail(username))
                .orElseThrow(() -> new UsernameNotFoundException("No account for the supplied email"));

        return new org.springframework.security.core.userdetails.User(
                user.getEmail(),
                user.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
