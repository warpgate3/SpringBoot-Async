package info.m2sj.asynctutorial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class AsyncTutorialApplication {
    private final static Logger log = LoggerFactory.getLogger(AsyncTutorialApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AsyncTutorialApplication.class, args);
    }

    @RestController
    @RequestMapping("/")
    static class AsyncController {

        @GetMapping("sync/{name}")
        public String sync(@PathVariable String name) throws InterruptedException {
            log.info(" ---> in sync");
            TimeUnit.SECONDS.sleep(10);
            return "[" + name + "]";
        }

        @GetMapping("callable/{name}")
        public Callable<String> callable(@PathVariable String name) {
            log.info(" ---> in callable");
            return () -> {
                TimeUnit.SECONDS.sleep(10);
                return "[" + name + "]";
            };
        }

        @GetMapping("deffer/{name}")
        public DeferredResult deferredResult(@PathVariable String name) {
            log.info("==> in deferredResult");
            DeferredResult<String> rtn = new DeferredResult<>();
            Runnable anotherThread = () -> {
                try {
                    TimeUnit.SECONDS.sleep(10);
                    rtn.setResult("[" + name + "]");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            };

            new Thread(anotherThread).start();

            return rtn;
        }
    }
}
