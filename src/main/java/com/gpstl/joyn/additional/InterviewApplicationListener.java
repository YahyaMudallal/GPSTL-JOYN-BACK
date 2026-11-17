package com.gpstl.joyn.additional;

import com.gpstl.joyn.application.event.ApplicationStatusChanged;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Component
public class InterviewApplicationListener {
    private final JdbcTemplate jdbc;
    public InterviewApplicationListener(JdbcTemplate jdbc){this.jdbc=jdbc;}
    @EventListener
    @Transactional
    public void changed(ApplicationStatusChanged event){
        if(Set.of("WITHDRAWN","REJECTED","ACCEPTED").contains(event.statusCode()))
            jdbc.update("UPDATE interviews SET status='CANCELLED' WHERE application_id=? AND status IN ('PROPOSED','CONFIRMED')",event.applicationId());
    }
}
