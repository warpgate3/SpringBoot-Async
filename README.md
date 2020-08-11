## 1\. Overview

Blocking/Non Blocking 의미는 차치하고 결국 이런 단어를 언급해 설명하고자 하는 건 요청이 있고 해당 요청에 대한 처리 작업 시간이 길더라도 wait 하지 않고 얼마나 자원을 효율적으로 사용할 수 있는가에 초점이 있다. 오늘은 Spring Boot로  동작 가능한 프로그램을 작성해 실제로 어떻게 동작하는지 확인해보겠다.

## 2\. Servlet Thread  (Blocking)

tomcat은 default 로 200개의 써블릿 쓰레드를 설정해서 요청을 처리한다. 만약에 200개의 쓰레드가 모두 사용 중이라면 이후 요청은 서블릿 쓰레드가 확보 될 때까지 대기를 해야된다. 테스트를 쉽게 하기 위해서 톰캣의 스레드 갯수를 1개로 변경하고 테스트를 하겠다.

### 2.1 application.yml

spring boot 에서는 application.yml 파일에 설정으로 쓰레드의 갯수를 변경할 수 있다.

```
server:
  tomcat:
    threads:
      max: 1

```

### 2.2 Controller

테스트를 위한 간단 controller 를 만들겠다. 이름을 Path Parameter 전달하면 10초후에 대괄호를 씌워 반환한다.

```
    @GetMapping("sync/{name}")
    public String sync(@PathVariable String name) throws InterruptedException {
    	log.info(" ---> in sync");
    	TimeUnit.SECONDS.sleep(10);
    	return "[" + name + "]";
    }
```

### 2.3 테스트

Spring Boot서버를 시작하고 URL을 요청해 보겠다. 최초 1회 요청 후 F5 를 눌러 재 요청하거나 브라우저 탭을 여러게 띄어서 계속적으로 요청해보겠다. 해당 메서드에서 10초를 대기하기 때문에 첫번째 요청이 끝나기까지 나머지 요청은 Pending 된다.

[##_Image|kage@Vqwow/btqGfNzrgCD/VCQeeKx2gYMKBbEedDufIk/img.png|alignLeft|data-origin-width="0" data-origin-height="0" data-ke-mobilestyle="widthContent"|||_##]

 출력된 로그 문자열(\---> in sync)로 확인할 수 있듯이 브라우저에서 아무리 요청을 많이 보내도 10초에 한번씩 출력된다. tomcat 의 max threads 숫자를 높이면 해당하는 숫자만큼  동시 처리가 가능할 것이다.

## 3\. Servlet Thread (Non Blocking)

이제 같은 환경에서 Non Blocking 으로 요청을 처리하는 몇가지 방법을 알아보겠다. 

### 3.1 Callable

Callable 은 Java에서 Multi Threading 처리를 위해 Java 1.5 에서 추가된 인터페이스이다. Servlet 3.0 부터는 Servlet 에서 리턴값을 Callable 을 리턴하면 비동기 처리가 가능하다. Spring @MVC 3.2 이상에서도 해당 스펙을 지원한다. 아래 코드는 Callable 리턴하는 예제 코드이다.

```
 @GetMapping("callable/{name}")
        public Callable<String> getName(@PathVariable String name) {
            log.info(" ---> in callable");
            return () -> {
                TimeUnit.SECONDS.sleep(10);
                return "[" + name + "]";
            };
        }
```

자바8 lamda를 이용해 Callable 을 구현한 익명함수를 반환했다.

### 3.2 Callable 테스트 

10초에 delay 타임이 있기 때문에 응답이 바로오지는 않지만 로그를 확인해 보면 요청이 올때마다 바로 메서드에 진입하는걸 확인할 수있다.  처음에 Node.js 가 세상에 나오면서 Java Servlet 에 비교해 빠른 응답과 많은 처리량을 할 수 있던 차이점이 이와 비슷한 맥락이다.

[##_Image|kage@bXSjGz/btqGfOecH09/JzL8AmFRH4sSdf7QoOg0x0/img.png|alignLeft|data-origin-width="0" data-origin-height="0" data-ke-mobilestyle="widthContent"|브라우저에서 재요청 할때마다 ---&gt; in callable 메서드가 출력된다.||_##]

### 3.3 DefferedResult

DefferedResult 역시 Spring @MVC 3.2 이상에서 Servlet 의 비동기 처리를 위한 객체이다. Controller 에서 DefferedResult 를 생성해서 반환하고 이후에 해당 객체의 메서드로 종료 처리 또는 예외 처리가 가능하다. Callable 과 가장 큰 차이점은 비동기 처리를 위한 쓰레드 작업이 내부가 아닌 외부에서 관리 된다는 점이다. 

```
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
```

Controller 에서는 DefferedResult<String> 객체를 생성해서 반환만 해준다. 그리고 별도의 쓰레드에서 작업이 완료되면 setResult() 메서드를 호출해서 종결처리를 한다. 또는s etErrorResult() 메서드를 이용해서 에러 처리를 할 수 있다. 이 부분은 자바스크립트에서 비동기 처리를 할때 언급되는 callback 과 Promise 패턴의 차이점이기도 하다.

### 3.4 DefferedResult

DefferedResult 를 이용한 URI 호출 역시 Callable 과 마찬가지로 페이지 요청마다 로그가 출력되는걸 확인할 수 있다.

[##_Image|kage@bLBtNg/btqGfNNdhYK/cDSAQSiHWtVXoc6TB6nkNK/img.png|alignLeft|data-origin-width="0" data-origin-height="0" data-ke-mobilestyle="widthContent"|브라우저에서 재요청 할때마다 ==&gt; in defferedResult 출력된다.||_##]

## 4\. Conclusion

간략한 예제를 이용해 Spring Boot에서 Cotroller의 비동기 응답처리를 하는 법을 알아봤다. 물론 세부적 자원에 대한 고려 사항이 더욱 많을 수 있겠지만 단편적인 부분만 봤을 때 많은 요청을 처리하기 위한 좋은 방법이 될 수 있을 것 같다. 예제 코드 전체는 아래 링크에 가면 확인할 수 있다.

_**[github.com/warpgate3/async-springboot](https://github.com/warpgate3/async-springboot)**_

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
