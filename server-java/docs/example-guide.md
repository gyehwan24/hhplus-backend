- 예시코드
    - **프로젝트 구조**
        
        ```java
        src/
         ├ interfaces/          # UI · API
         │   └ web/OrderController.java
         ├ application/         # 유스케이스
         │   └ PlaceOrderService.java
         ├ domain/              # 순수 모델·규칙
         │   ├ model/Order.java
         │   └ repository/OrderRepository.java
         └ infrastructure/
             └ persistence/
                 ├ OrderEntity.java          # JPA DTO
                 └ OrderJpaRepository.java   # 어댑터(매핑 포함)
        ```
        
    - **Domain 모델 차이**
        - **이전 - JPA 의존 엔티티**
            
            ```java
            @Entity
            @Table(name="orders")
            public class Order {          // ← JPA에 직접 의존
                @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
                private Long id;
                private String productName;
                private int quantity;
                private int unitPrice;
                private LocalDateTime orderedAt;
            
                protected Order() {}                      // JPA 기본 생성자
                private Order(...) { ... }                // 공장 메서드 create()
                public int totalPrice() { ... }           // 규칙 OK
            }
            ```
            
        - 이후 - **순수 POJO + JPA DTO 분리**
            
            ```java
            // domain/model/Order.java          ← **JPA 어노테이션 삭제**
            public class Order {
                private Long id;
                private final String productName;
                private final int quantity;
                private final int unitPrice;
                private final LocalDateTime orderedAt;
            
                private Order(String p,int q,int up){ ... }
                public static Order create(String p,int q,int up){ ... }
                public int totalPrice(){ ... }
                void assignId(Long id){ this.id=id; }     // 인프라만 접근
            }
            ```
            
            ```java
            // infrastructure/persistence/OrderEntity.java
            @Entity @Table(name="orders")
            class OrderEntity {                           // JPA 전용 DTO
                @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
                Long id;
                String productName;
                int quantity;
                int unitPrice;
                LocalDateTime orderedAt;
            }
            ```
            
        - **핵심 규칙 보호**: 도메인 모델에 JPA 어노테이션이 사라지면서, 비즈니스 로직이 *“어떤 DB·ORM을 쓰는지”* 전혀 모르게 됨
        - **프레임워크 교체 자유**: RDB → Mongo / Elastic 같은 저장소 전환 시에도 Order 코드는 **단 한 줄도 수정되지 않음**—새 인프라 DTO·어댑터만 추가하면 끝
        - **모델 경량화**: JPA용 기본 생성자·프록시 보호 등 불필요한 보일러플레이트 제거 → 도메인 클래스가 읽기 쉬워지고 핵심 규칙(총액 계산 등)이 눈에 잘 들어옴
    - **Service / Use-Case 레이어**
        - **이전**
            
            ```java
            package com.example.service;
            
            import com.example.entity.Order;
            import com.example.repository.OrderRepository;
            import org.springframework.stereotype.Service;
            import org.springframework.transaction.annotation.Transactional;
            
            @Service
            public class OrderService {
            
                private final OrderRepository orderRepository;   // Spring-Data Repo에 바로 의존
            
                public OrderService(OrderRepository orderRepository) {
                    this.orderRepository = orderRepository;
                }
            
                /**
                 * 단일 유스케이스: 주문 생성
                 * - 도메인 객체 생성
                 * - JPA Repository 로 바로 저장
                 */
                @Transactional
                public Order placeOrder(String productName, int qty, int unitPrice) {
                    Order order = Order.create(productName, qty, unitPrice);
                    return orderRepository.save(order);          // 영속화 세부 사항 직접 호출
                }
            }
            ```
            
        - 이후
            
            ```java
            package com.example.application;
            
            import com.example.domain.model.Order;
            import com.example.domain.repository.OrderRepository;
            
            /**
             * 순수 유스케이스 클래스
             * - @Service, 스프링 의존성 제거 (DI는 구성 클래스에서)
             * - 트랜잭션 경계만 남기고, 프레임워크·DB 세부 지식 없음
             */
            public class PlaceOrderService {
            
                private final OrderRepository orderRepository;   // 도메인 포트(인터페이스)에만 의존
            
                public PlaceOrderService(OrderRepository orderRepository) {
                    this.orderRepository = orderRepository;
                }
            
                /** 주문 생성 유스케이스 */
                @org.springframework.transaction.annotation.Transactional
                public Order place(String productName, int qty, int unitPrice) {
                    Order order = Order.create(productName, qty, unitPrice);
                    return orderRepository.save(order);          // 추상 포트 호출
                }
            }
            ```
            
        - 이름을 **“무엇을 하는가(PlaceOrder)”** 로 명시 → 유스케이스 의미 강조
        - 스프링 어노테이션·프레임워크 의존 제거 → 테스트 용이
        - 의존 대상이 **Spring Data Repo ⟶ 도메인 인터페이스**
    - **Repository 구현**
        - **이전**
            
            ```java
            public interface OrderRepository   // Spring Data 직접 상속
                   extends JpaRepository<Order, Long> { }
            ```
            
        - 이후
            
            ```java
            // domain.repository.OrderRepository
            public interface OrderRepository {
                Order save(Order order);
            }
            ```
            
            ```java
            // infrastructure.persistence.OrderJpaRepository
            @Repository
            class OrderJpaRepository implements OrderRepository {
                private final SpringOrderJpa jpa;     // 내부 JPA Repo
            
                public Order save(Order o) {
                    OrderEntity e = toEntity(o);
                    OrderEntity saved = jpa.save(e);
                    o.assignId(saved.id);
                    return o;
                }
                
                /** Domain → JPA DTO 매핑 (Infra 전용) */
                private OrderEntity toEntity(Order o) {
                    OrderEntity e = new OrderEntity();
                    e.productName = o.getProductName();
                    e.quantity    = o.getQuantity();
                    e.unitPrice   = o.getUnitPrice();
                    e.orderedAt   = o.getOrderedAt();
                    return e;
                }
            }
            ```
            
        - domain.repository.OrderRepository 가 *추상 포트*
        - JPA 구현은 Infrastructure 레이어로 이동
        - toEntity **왜 Infrastructure 쪽인가?**
            - **도메인 순수성 유지**
                - Order 가 JPA 엔티티를 알게 되면 다시 프레임워크에 결합됩니다. 매핑 책임을 바깥(Infra)으로 밀어야 “도메인은 프레임워크 무지식” 상태가 유지됩니다.
            - **의존성 방향**
                - 의존성 화살표가 *Domain ← Infrastructure* 로 향해야 DIP/OCP가 성립합니다. 매퍼가 Infra 안에 있으면 Order 는 모르는 반면, 어댑터는 두 타입을 모두 볼 수 있습니다.
            - **구현 세부사항 격리**
                - JPA, QueryDSL, MapStruct 등 어떤 라이브러리를 쓰든 **Infra 내부**에서 바꿔 끼우면 되므로 도메인·애플리케이션층은 영향을 받지 않습니다.
    - **Controller (거의 그대로)**
        
        ```java
        @PostMapping
        public ResponseEntity<Res> create(@RequestBody Req r){
            Order o = placeOrderService.place(
                         r.product(), r.qty(), r.unitPrice());
            return ResponseEntity.ok(new Res(o.getId(),o.totalPrice()));
        }
        ```
        
        - 단지 **PlaceOrderService** 타입이 바뀌었을 뿐 로직은 동일
        - 여전히 HTTP ↔ 도메인 호출 변환만 담당
    - NestJS
        - 전체 구조 요약
            
            ```tsx
            src/
            ├ interfaces/              
            │   └─ web/                    # Controller (UI 레이어)
            │       └─ order.controller.ts
            ├ application/                # Use-case 계층
            │   └─ place-order.service.ts
            ├ domain/                     # 순수 도메인 + 포트
            │   ├─ model/
            │   │   └─ order.ts
            │   └─ repository/
            │       └─ order.repository.ts
            └ infrastructure/
                └─ persistence/
                    ├─ order.entity.ts        # TypeORM 전용 엔티티
                    ├─ spring-order.repo.ts   # TypeORM Repo
                    └─ order-jpa.repository.ts # 실제 어댑터 구현체
            
            ```
            
        - Domain Model (순수 비즈니스 로직)
            
            ```tsx
            // domain/model/order.ts
            export class Order {
              private id?: number;
            
              constructor(
                private readonly productName: string,
                private readonly quantity: number,
                private readonly unitPrice: number,
                private readonly orderedAt: Date = new Date(),
              ) {}
            
              static create(productName: string, quantity: number, unitPrice: number): Order {
                return new Order(productName, quantity, unitPrice);
              }
            
              assignId(id: number) {
                this.id = id;
              }
            
              totalPrice(): number {
                return this.quantity * this.unitPrice;
              }
            
              getId() { return this.id; }
              getProductName() { return this.productName; }
              getQuantity() { return this.quantity; }
              getUnitPrice() { return this.unitPrice; }
              getOrderedAt() { return this.orderedAt; }
            }
            
            ```
            
        - Domain Port (Repository 인터페이스)
            
            ```tsx
            // domain/repository/order.repository.ts
            import { Order } from '../model/order';
            
            export interface OrderRepository {
              save(order: Order): Promise<Order>;
            }
            
            ```
            
        - Infrastructure Entity (ORM 전용 DTO)
            
            ```tsx
            // infrastructure/persistence/order.entity.ts
            import { Entity, Column, PrimaryGeneratedColumn } from 'typeorm';
            
            @Entity('orders')
            export class OrderEntity {
              @PrimaryGeneratedColumn()
              id: number;
            
              @Column()
              productName: string;
            
              @Column()
              quantity: number;
            
              @Column()
              unitPrice: number;
            
              @Column()
              orderedAt: Date;
            }
            
            ```
            
        - Infrastructure Adapter (Repository 구현)
            
            ```tsx
            // infrastructure/persistence/order-jpa.repository.ts
            import { Injectable } from '@nestjs/common';
            import { InjectRepository } from '@nestjs/typeorm';
            import { Repository } from 'typeorm';
            import { OrderRepository } from '../../domain/repository/order.repository';
            import { Order } from '../../domain/model/order';
            import { OrderEntity } from './order.entity';
            
            @Injectable()
            export class OrderJpaRepository implements OrderRepository {
              constructor(
                @InjectRepository(OrderEntity)
                private readonly repo: Repository<OrderEntity>,
              ) {}
            
              async save(order: Order): Promise<Order> {
                const entity = this.toEntity(order);
                const saved = await this.repo.save(entity);
                order.assignId(saved.id);
                return order;
              }
            
              private toEntity(order: Order): OrderEntity {
                const entity = new OrderEntity();
                entity.productName = order.getProductName();
                entity.quantity = order.getQuantity();
                entity.unitPrice = order.getUnitPrice();
                entity.orderedAt = order.getOrderedAt();
                return entity;
              }
            }
            
            ```
            
        - Application Layer (Use-case / Service)
            
            ```tsx
            // application/place-order.service.ts
            import { Injectable } from '@nestjs/common';
            import { Order } from '../domain/model/order';
            import { OrderRepository } from '../domain/repository/order.repository';
            
            @Injectable()
            export class PlaceOrderService {
              constructor(private readonly orderRepository: OrderRepository) {}
            
              async place(productName: string, quantity: number, unitPrice: number): Promise<Order> {
                const order = Order.create(productName, quantity, unitPrice);
                return this.orderRepository.save(order);
              }
            }
            
            ```
            
        - Controller (UI 계층)
            
            ```tsx
            // interfaces/web/order.controller.ts
            import {
              Body,
              Controller,
              Post,
              HttpCode,
              HttpStatus,
            } from '@nestjs/common';
            import { PlaceOrderService } from '../../application/place-order.service';
            import { Order } from '../../domain/model/order';
            
            class CreateOrderRequest {
              productName: string;
              quantity: number;
              unitPrice: number;
            }
            
            class OrderResponse {
              constructor(
                public readonly id: number,
                public readonly totalPrice: number,
              ) {}
            }
            
            @Controller('orders')
            export class OrderController {
              constructor(private readonly placeOrderService: PlaceOrderService) {}
            
              @Post()
              @HttpCode(HttpStatus.CREATED)
              async create(@Body() req: CreateOrderRequest): Promise<OrderResponse> {
                const order: Order = await this.placeOrderService.place(
                  req.productName,
                  req.quantity,
                  req.unitPrice,
                );
                return new OrderResponse(order.getId()!, order.totalPrice());
              }
            }
            
            ```
            
        - DI 등록 (AppModule)
            
            ```tsx
            // app.module.ts
            import { Module } from '@nestjs/common';
            import { TypeOrmModule } from '@nestjs/typeorm';
            import { OrderController } from './interfaces/web/order.controller';
            import { PlaceOrderService } from './application/place-order.service';
            import { OrderJpaRepository } from './infrastructure/persistence/order-jpa.repository';
            import { OrderEntity } from './infrastructure/persistence/order.entity';
            
            @Module({
              imports: [TypeOrmModule.forFeature([OrderEntity])],
              controllers: [OrderController],
              providers: [
                PlaceOrderService,
                OrderJpaRepository,
                {
                  provide: 'OrderRepository',
                  useExisting: OrderJpaRepository,
                },
              ],
            })
            export class AppModule {}
            
            ```
            
            - 적용 방식 요약
                - **Controller**: 여전히 `NestJS`의 `@Controller`를 사용해 요청을 받음.
                - **Service / Use-case**:
                    - `@Injectable()` 은 사용하지만, 내부적으로는 **비즈니스 시나리오 단위의 유스케이스**로 설계.
                    - 주입받는 대상은 TypeORM Repo가 아니라 **도메인 레이어의 인터페이스(Port)**.
                - **Domain**:
                    - `Order` 모델은 **POJO**로 프레임워크 의존 없이 비즈니스 로직에만 집중.
                - **Repository**:
                    - `OrderRepository` 는 **도메인 포트 (인터페이스)**.
                    - 실제 저장소 구현은 **infrastructure/persistence** 에 위치.
                    - `DIP(의존성 역전 원칙)` 을 따름 → 상위 모듈(Service)은 인터페이스에만 의존.
    - **📝 변화 효과 요약**
        
        
        | **항목** | **Before (전통 Layered)** | **After (Clean + Layered)** |
        | --- | --- | --- |
        | **도메인 순수성** | JPA 어노테이션, Spring 의존 | 의존 0 (POJO) |
        | **의존 방향** | Controller → Service → *SpringData*Repo | Controller → Application → *Domain Port* ← Infrastructure |
        | **테스트 난이도** | 도메인 테스트 시 스프링 컨텍스트 필요 | Port Mock 주입 - JVM 단위 테스트 OK |
        | **DB 교체 비용** | Service 메서드·엔티티 수정 필요 | Infrastructure 어댑터 추가/교체만 |
        | **학습 부담** | 낮음 | 소폭 상승(계층·패키지 구분) |
    - 결론
        - 코드는 거의 변하지 않지만 “핵심 규칙이 프레임워크 무관” 해져서
            - 기술 교체·멀티 채널(I/O) 대응이 쉬워지고,
            - 단위 테스트가 가벼워짐
            - 단순 CRUD만 있는 팀이라면 굳이 After 를 강제할 필요는 없고, 확장성·장기 유지보수가 예상될 때 점진적으로 전환하면 됩니다.
        - 실무 TIP: 계층 구조에 클린 아키텍처 도입
            - 처음부터 모든 것을 클린 아키텍처로 작성하는 게 부담스럽다면, 우선 **Repository 의존성 분리 도메인 로직과 기술적 구현을 구분**