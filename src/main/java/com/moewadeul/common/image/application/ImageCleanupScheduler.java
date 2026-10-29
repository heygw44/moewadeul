package com.moewadeul.common.image.application;

import com.moewadeul.common.scheduling.ScheduledJobRunner;
import com.moewadeul.common.time.KoreaTime;
import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

@Component
public class ImageCleanupScheduler implements SchedulingConfigurer {

    private final List<ImageCleanup> cleanups;
    private final ScheduledJobRunner jobRunner;
    private final Environment environment;

    public ImageCleanupScheduler(List<ImageCleanup> cleanups, ScheduledJobRunner jobRunner, Environment environment) {
        this.cleanups = cleanups;
        this.jobRunner = jobRunner;
        this.environment = environment;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        for (ImageCleanup cleanup : cleanups) {
            String key = cleanup.keyPrefix();
            String property = "moewadeul." + key + ".image-cleanup.";
            if (!environment.getProperty(property + "enabled", Boolean.class, true)) {
                continue;
            }
            String job = key + "-image-cleanup";
            jobRunner.register(job);
            CronTrigger trigger = new CronTrigger(environment.getRequiredProperty(property + "cron"), KoreaTime.ZONE);
            registrar.addCronTask(
                    new CronTask(() -> jobRunner.run(job, cleanup::deleteExpiredRowsThenObjects), trigger));
        }
    }
}
