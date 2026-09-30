package com.civicconnect.api;

import com.civicconnect.auth.AuthService;
import com.civicconnect.auth.JdbcUserRepository;
import com.civicconnect.auth.PasswordHasher;
import com.civicconnect.data.Database;
import com.civicconnect.data.JdbcServiceRequestRepository;
import com.civicconnect.data.TransactionRunner;
import com.civicconnect.lifecycle.RequestLifecycle;
import com.civicconnect.notification.JdbcNotificationStores;
import com.civicconnect.notification.Listeners;
import com.civicconnect.notification.MessageChannel;
import com.civicconnect.notification.OutboxDispatcher;
import com.civicconnect.notification.OutboxRepository;
import com.civicconnect.notification.StatusChangePublisher;
import com.civicconnect.service.JdbcRequestQueries;
import com.civicconnect.service.NotificationService;
import com.civicconnect.service.RequestService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import javax.sql.DataSource;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Composition root for the backend (Person 2). Separate from Rethabile's AppContextListener so
 * neither module has to edit the other's start-up code; both obtain the same pooled JNDI DataSource.
 * <p>
 * Context parameter {@code civicconnect.simulatedFailEveryNth} (optional, default 0) makes the
 * simulated channels fail every Nth message, to demonstrate retries.
 */
@WebListener
public class BackendContextListener implements ServletContextListener {

    private ScheduledExecutorService scheduler;

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext ctx = event.getServletContext();
        DataSource dataSource = Database.lookup();
        TransactionRunner tx = new TransactionRunner(dataSource);
        Clock clock = Clock.systemUTC();

        JdbcUserRepository users = new JdbcUserRepository(tx);
        JdbcNotificationStores.Notifications notifications = new JdbcNotificationStores.Notifications(tx);
        JdbcNotificationStores.Outbox outbox = new JdbcNotificationStores.Outbox(tx);

        StatusChangePublisher publisher = new StatusChangePublisher(List.of(
                new Listeners.InAppNotificationListener(notifications),
                new Listeners.SimulatedMessagingListener(outbox)));

        RequestService requests = new RequestService(new JdbcServiceRequestRepository(tx),
                new JdbcRequestQueries(tx), users, outbox, new RequestLifecycle(), publisher, clock);
        AuthService auth = new AuthService(users, new PasswordHasher(), clock);

        ctx.setAttribute(BackendServices.ATTRIBUTE,
                new BackendServices(auth, requests, new NotificationService(notifications), dataSource));

        int failEveryNth = parseInt(ctx.getInitParameter("civicconnect.simulatedFailEveryNth"));
        OutboxDispatcher dispatcher = new OutboxDispatcher(outbox, List.of(
                new MessageChannel.Simulated(OutboxRepository.Channel.SMS, failEveryNth),
                new MessageChannel.Simulated(OutboxRepository.Channel.WHATSAPP, failEveryNth)), clock);
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "civic-outbox-dispatcher");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(dispatcher, 5, 5, TimeUnit.SECONDS);
        ctx.log("CivicConnect backend (auth, lifecycle, notifications, /api) initialised");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        if (scheduler != null) scheduler.shutdownNow();
    }

    private static int parseInt(String value) {
        try {
            return value == null ? 0 : Math.max(0, Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
