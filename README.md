## 1\. Overview

지난번에 Controller 에서 비동기 처리 하는법을 알아봤다. 오늘은 Service 계층에서 비동기 처리를 위한 몇가지 방법을 간단한 예제로 알아보겠다.

## 2\. Asynchronous Service

### 2.1 Future

Future 는 Java 1.5 부터 java.util.concurrent 패키지에 추가된 비동기 처리를 위한 인터페이스다. Future 를 이용해서 비동기 처리를 할 수 있는 서비스를 만들어서 테스트 해보자. 10초를 대기하고 이후 입력받은 이름에 대괄호를 씌어 반환하는 메서드이다.

```
 public Future<String> getName(String name) {
 	final ExecutorService executorService = Executors.newSingleThreadExecutor();
 	return executorService.submit(() -> {
 		TimeUnit.SECONDS.sleep(10);
 		return "[" + name + "]";
 	});
 }
```

위와 같이 ExecutorService 의 submit 메서드에 람다식을 이용 반환값을 Future 로 지정해서  사용할 수 있지만, Future 를 구현하고 있는 FutureTask 를 직접 생성해서 사용할 수도 있다. execute() 메서드는 인자로 FutureTask를 받을수 있는건 FutureTask 가 내부적으로 Runnable 인터페이스를 구현하고 있기 때문이다.

```
public Future<String> getName(String name) {
            final ExecutorService executorService = Executors.newSingleThreadExecutor();

            FutureTask<String> future = new FutureTask<>(() -> {
                SECONDS.sleep(10);
                return "[" + name + "]";
            });
            executorService.execute(future);
            return future;
}
```

getName을 호출 하는 Controller 를 만들겠다.

```
 @RestController
    @RequestMapping("/")
    static class AsyncController {
        @Autowired
        private AsyncService asyncService;

        @GetMapping("future/{name}")
        public String future(@PathVariable String name) throws InterruptedException, ExecutionException {
            final Future<String> futureName = asyncService.getName(name);
            final String resultName = futureName.get();
            log.info("expect to print this line");
            return resultName;
        }
```

스프링 부트 서버를 시작하고 ~/future/m2sj 를 호출하면 10초후에 화면 \[m2sj\] 를 출력하는걸 확인할 수 있다. get() 메서드를 호출하는 순간 FutureTask 의 작업이 시작되고 10초후에 resultName 을 반환한다. 하지만 get() 메서드를 호출하는 현재 쓰레드는 Blocking 된다. 우리가 원하는건 FutureTask 작업이 길더라도 아래 log.info("expect to print this line"); 라인이 바로 실행되길 바라지만 말이다. 이 부분이 Future 를 이용한 비동기 처리의 한계점이다. 

### 2.2 ListenableFuture

위에서 언급했듯 Future 는 응답이 끝날때까지 blocking이 된다. 그걸 보완한 Future 를 구현하는 ListenableFuture 라는 클래스를 스프링 4.0 부터 지원하기 시작했다. ListenableFuture 클래스는 Future 에 callback 메서드를 이용하는 방법이다. 즉 Future.get 을 기다려서 처리하는 게 아니라 작업이 끝날경우 처리해야 할 callback 메서드로 정의하는 것 이다. 예제를 통해 알아보겠다.

```
 public ListenableFuture<String> getNameByListen(String name) {
            SimpleAsyncTaskExecutor t = new SimpleAsyncTaskExecutor();
            return t.submitListenable(() -> {
                SECONDS.sleep(10);
                return "[" + name + "]";
            });
        }
```

ListenableFuture 를 리턴하는 SimpleAsyncTaskExecutor 가 SpringBoot 2.0 부터 지원한다. 해당 Executor 를 생성해서 처리하겠다. **예제에서는 간결한 테스트를 위해 메서드안에서 직접 생성했지만 실무에서는 스프링 Bean으로 생성해서 싱글톤으로 관리해야된다. 이 내용은 위에서 언급한 Future 예제의 final ExecutorService executorService = Executors.newSingleThreadExecutor(); 이 부분도 마찬가지이다.**

```
 @GetMapping("listenable/{name}")
        public ListenableFuture<String> listenable(@PathVariable String name) {
            final ListenableFuture<String> nameByListen = asyncService.getNameByListen(name);
            nameByListen.addCallback(
                name -> {}, 
                 e -> {
                throw new RuntimeException();
                });
            log.info("expect to print this line");
            return nameByListen;
        }
```

addCallback 메서드를 이용해서 2개의 함수를 인수로 전달했다. 첫 번째는 SucessCallback 이고 두번째 인자는 FailCallback 이다. Sucess일 경우 이 예제에서는 별 도의 처리는 없기 때문에 빈 함수를 넣었다. 이제 ~/listenable/m2sj 를 요청하면 blocking 없이 "expect to print this line" 로그를 확인할 수 있다.

### 2.3 CompletableFuture

아마 언어에 상관없이 비동기 작업을 위해 callback 함수를 이용해본 사람은 누구나 한번쯤 듣거나 겪어 봤을 것이다. Callback 지옥을 .. 여러개의 비동기 처리를 작업할때 callback 안에서 callback 그 안에서 또 callback 을 호출해야 된다. callback 이 3개만 중첩 그 때 부터는 유지 보수나 코드 가독성이 떨어진다. ListeableFuture 는 callback 패턴을 이용하기 때문에 그러한 단점이 존재하다. 

```
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
```

위 코드와 같은 헬을 해결할 수 있는 클래스가 자바 1.8부터 지원하는 CompletableFuture 이다.   
우선 CompletableFuture 로 변경한 코드를 먼저 살펴보자  

```
@GetMapping("comple/{name}")
        public CompletableFuture<String> comple(@PathVariable String name)  {
            return asyncService.getNameByComple1(name)
                    .thenCompose(asyncService::getNameByComple2)
                    .thenCompose(asyncService::getNameByComple3);
        }
```

비교도 안될만큼 간결하다. 각각의 getNameByComple 메서드들은 모두 CompletableFuture<String> 을 반환하는 메서드이다.  

```
 public CompletableFuture<String> getNameByComple1(String name) {
            return CompletableFuture.supplyAsync(() -> {
                try {
                    SECONDS.sleep(3);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                return "(" + name + ")";
            });
        }
```

CompletableFuture 의 thenCompose 메서드를 이용해서 각 메서드의 결과 값들을 파이프 라인 형태로 처리한 것이다. 가독성이 좋아지고 코드가 무척 간결해졌다. CompletableFuture 메서드는 그외 많은 메서드가 존재하는데 아래 링크를 참고하면 좋다.

2.4 @Async  

꼴레리

## 3\. Conclusion

Java & Spring 에서의 비동기 처리를 몇 가지 클래스를 소개했지만 비동기 처리를 위한 여러가지 개념과 API 실무에서 제대로 활용하기 위해서는  많은 학습이 필요하다. 전체 코드는 아래 GITHUB 링크를 참고하면 된다.  
[github.com/warpgate3/async-springboot.git](https://github.com/warpgate3/async-springboot.git)  

참고링크

---

[https://dzone.com/articles/20-examples-of-using-javas-completablefuture](https://dzone.com/articles/20-examples-of-using-javas-completablefuture)  

[https://github.com/google/guava/wiki/ListenableFutureExplained](https://github.com/google/guava/wiki/ListenableFutureExplained)

[https://www.baeldung.com/java-asynchronous-programming](https://www.baeldung.com/java-asynchronous-programming)

[https://www.baeldung.com/spring-async](https://www.baeldung.com/spring-async)
