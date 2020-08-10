package info.m2sj.asynctutorial;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.task.AsyncListenableTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;
import org.springframework.util.concurrent.ListenableFuture;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.TimeUnit.SECONDS;

@SpringBootApplication
public class AsyncControllerApplication {
    private final static Logger log = LoggerFactory.getLogger(AsyncControllerApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AsyncControllerApplication.class, args);
    }

    @RestController
    @RequestMapping("/")
    static class AsyncController {
        @Autowired
        private AsyncService asyncService;

        @GetMapping("future/{name}")
        public Future<String> future(@PathVariable String name) throws InterruptedException, ExecutionException {
            final Future<String> futureName = asyncService.getName(name);
//            final String resultName = futureName.get();
            log.info("expect to print this line");
            return futureName;
        }


        @GetMapping("listenable/{name}")
        public ListenableFuture<String> listenable(@PathVariable String name) {
            final ListenableFuture<String> nameByListen = asyncService.getNameByListen(name);
            nameByListen.addCallback(string -> string = "[" + string + "]", e -> {
                throw new RuntimeException();
            });
            log.info("expect to print this line");
            return nameByListen;
        }

        @GetMapping("callback-hell/{name}")
        public DeferredResult<StringBuilder> callbackHell(@PathVariable String name) {
            DeferredResult<StringBuilder> rtn = new DeferredResult<>();
            asyncService.getNameByListen1(new StringBuilder(name)).addCallback(n -> {
                asyncService.getNameByListen2(n).addCallback(n2 -> {
                    asyncService.getNameByListen3(n2).addCallback(n3 -> {
                        rtn.setResult(n3);
                    }, e -> {
                    });
                }, e -> {
                });
            }, e -> {
            });
            return rtn;
        }

        @GetMapping("sync/{name}")
        public String sync(@PathVariable String name) throws InterruptedException {
            log.info(" ---> in sync");
            SECONDS.sleep(10);
            return "[" + name + "]";
        }

        @GetMapping("callable/{name}")
        public Callable<String> callable(@PathVariable String name) {
            log.info(" ---> in callable");
            return () -> {
                SECONDS.sleep(10);
                return "[" + name + "]";
            };
        }

        @GetMapping("deffer/{name}")
        public DeferredResult deferredResult(@PathVariable String name) {
            log.info("==> in deferredResult");
            DeferredResult<String> rtn = new DeferredResult<>();
            Runnable anotherThread = () -> {
                try {
                    SECONDS.sleep(10);
                    rtn.setResult("[" + name + "]");
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            };

            new Thread(anotherThread).start();

            return rtn;
        }
    }


    @Service
    static class AsyncService {
        public ListenableFuture<String> getNameByListen(String name) {
            AsyncListenableTaskExecutor t = new SimpleAsyncTaskExecutor();
            return t.submitListenable(() -> {
                SECONDS.sleep(10);
                return "(" + name + ")";
            });
        }

        public ListenableFuture<StringBuilder> getNameByListen1(StringBuilder name) {
            AsyncListenableTaskExecutor t = new SimpleAsyncTaskExecutor();
            return t.submitListenable(() -> {
                SECONDS.sleep(3);
                return new StringBuilder("(" + name.toString() + ")");
            });
        }

        public ListenableFuture<StringBuilder> getNameByListen2(StringBuilder name) {
            AsyncListenableTaskExecutor t = new SimpleAsyncTaskExecutor();
            return t.submitListenable(() -> {
                SECONDS.sleep(3);
                return new StringBuilder("{" + name.toString() + "}");
            });
        }

        public ListenableFuture<StringBuilder> getNameByListen3(StringBuilder name) {
            AsyncListenableTaskExecutor t = new SimpleAsyncTaskExecutor();
            return t.submitListenable(() -> {
                SECONDS.sleep(3);
                return new StringBuilder("[" + name.toString() + "]");
            });
        }

        public Future<String> getName(String name) {
            final ExecutorService executorService = Executors.newSingleThreadExecutor();
//            return executorService.submit(() -> {
//                SECONDS.sleep(10);
//                return "[" + name + "]";
//            });
            FutureTask<String> future = new FutureTask<>(() -> {
                SECONDS.sleep(10);
                return "[" + name + "]";
            });
            executorService.execute(future);
            return future;
        }
    }
}
