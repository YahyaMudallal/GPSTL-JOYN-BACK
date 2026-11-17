package com.gpstl.joyn.additional;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Explicit one-shot server-side command; never a public registration endpoint. */
@Component
@ConditionalOnProperty(name="joyn.admin.provision.enabled",havingValue="true")
public class PrivateAdminProvisioner implements ApplicationRunner {
    final JdbcTemplate jdbc;final PasswordEncoder passwords;final ConfigurableApplicationContext context;
    final TransactionTemplate tx;
    public PrivateAdminProvisioner(JdbcTemplate jdbc,PasswordEncoder passwords,ConfigurableApplicationContext context,PlatformTransactionManager tm){
        this.jdbc=jdbc;this.passwords=passwords;this.context=context;this.tx=new TransactionTemplate(tm);
    }
    @Override public void run(ApplicationArguments args){
        String email=System.getenv("JOYN_ADMIN_EMAIL"),password=System.getenv("JOYN_ADMIN_PASSWORD");
        if(email==null || !email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+") || password==null || password.length()<16 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)
            throw new IllegalArgumentException("JOYN_ADMIN_EMAIL and a private password (16 to 72 UTF-8 bytes) are required");
        String normalized=email.trim().toLowerCase(java.util.Locale.ROOT);
        tx.executeWithoutResult(status->{
            if(jdbc.queryForObject("SELECT count(*) FROM users WHERE lower(email)=?",Long.class,normalized)>0)
                throw new IllegalStateException("Email already exists; no account was promoted or overwritten");
            jdbc.update("INSERT INTO users(email,password_hash,role,status) VALUES(?,?,'ADMIN','ACTIVE')",normalized,passwords.encode(password));
            // Revoke the public development admin if it is still using the shipped password.
            for(var row:jdbc.queryForList("SELECT id,password_hash FROM users WHERE email='admin@joyn.test' AND role='ADMIN' FOR UPDATE"))
                if(passwords.matches("JoynAdmin2026!",(String)row.get("password_hash")))
                    jdbc.update("UPDATE users SET status='SUSPENDED',tokens_valid_after=now() WHERE id=?",row.get("id"));
        });
        System.out.println("Private administrator created; development credential revoked if unchanged.");
        context.close();
    }
}
