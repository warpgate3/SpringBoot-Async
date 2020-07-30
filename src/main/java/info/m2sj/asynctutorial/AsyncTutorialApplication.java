package info.m2sj.asynctutorial;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
//@EnableAsync
public class AsyncTutorialApplication {

    public static void main(String[] args) {
        SpringApplication.run(AsyncTutorialApplication.class, args);
    }

    @RestController
    @RequestMapping("/")
    static class AsyncController {

        @GetMapping("async/{name}")
        public Callable<String> getName(@PathVariable String name) throws InterruptedException {
            System.out.println("==> in the method");
            return () -> {
                TimeUnit.SECONDS.sleep(10);
                return "[" + name + "]";
            };
        }

        @GetMapping("sync/{name}")
        public String getName2(@PathVariable String name) throws InterruptedException {
            System.out.println("==> in the method 2");
            TimeUnit.SECONDS.sleep(10);
            return "[" + name + "]";
        }
    }
}
