# Documentación del Microservicio ms-order-product

## Tabla de Contenido
- [Introducción](#introducción)
- [Características Clave](#características-clave)
- [Arquitectura](#arquitectura)
- [Estructura del Proyecto](#estructura-del-proyecto)
  - [src/main/java/com/suresell/orders](#srcmainjavacomsuresellorders)
    - [application](#application)
      - [dto](#dto)
      - [usecase](#usecase)
    - [domain](#domain)
      - [model](#model)
      - [port](#port)
        - [in](#in)
        - [out](#out)
      - [service](#service)
    - [infrastructure](#infrastructure)
      - [client](#client)
        - [adapter](#adapter)
      - [config](#config)
      - [persistence](#persistence)
        - [repository](#repository)
      - [web](#web)
        - [adapter](#adapter)
    - [shared](#shared)
      - [enums](#enums)
      - [exception](#exception)
      - [export](#export)
      - [mapper](#mapper)

## Introducción
El `ms-order-product` es un microservicio diseñado para gestionar el ciclo de vida de los pedidos dentro del ecosistema Suresell. Maneja diversas operaciones relacionadas con la creación de pedidos, el historial, los cierres financieros diarios y la gestión de cupones. Este microservicio ha sido desarrollado adhiriéndose a los principios de la Arquitectura Hexagonal, enfatizando una clara separación de responsabilidades, alta capacidad de prueba e independencia tecnológica.

## Características Clave
Este microservicio proporciona las siguientes funcionalidades principales:
-   **Creación y Gestión de Pedidos**: Permite la creación, actualización y gestión general de los pedidos de los clientes.
-   **Historial de Pedidos**: Mantiene un historial detallado de los cambios y eventos relacionados con los pedidos.
-   **Cierres Diarios (Cierre de Caja)**: Facilita el proceso de conciliación y cierre financiero diario.
-   **Gestión de Cupones**: Maneja la creación, aplicación y validación de cupones de descuento.
-   **Gestión de Pedidos de Entrega**: Gestiona la creación y el seguimiento del estado de los pedidos de entrega.

## Arquitectura
El microservicio `ms-order-product` está construido utilizando la **Arquitectura Hexagonal** (también conocida como arquitectura de Puertos y Adaptadores). Este estilo arquitectónico tiene como objetivo crear componentes de aplicación débilmente acoplados que pueden ser fácilmente probados y evolucionados.

**Principios Fundamentales Aplicados:**
-   **Separación de Responsabilidades**: Límites claros entre la lógica de negocio (dominio), la lógica de aplicación (casos de uso) y las dependencias externas (infraestructura).
-   **Inversión de Dependencias**: La capa de dominio no depende de la capa de infraestructura; en cambio, ambas dependen de interfaces abstractas (puertos) definidas en el dominio.
-   **Capacidad de Prueba**: La lógica central de negocio puede ser probada de forma aislada de las preocupaciones externas.
-   **Independencia Tecnológica**: El núcleo de la aplicación puede ser impulsado por diferentes agentes externos (por ejemplo, una interfaz de usuario web, una cola de mensajes) y conectarse a varios sistemas externos (por ejemplo, diferentes bases de datos, APIs externas) sin cambiar la lógica central de negocio.

La arquitectura sigue estrictamente los **principios SOLID** e incorpora **mejores prácticas** y **patrones de diseño** para garantizar una base de código robusta, mantenible y escalable.

## Estructura del Proyecto
El proyecto sigue una estructura estándar de proyecto Maven/Gradle con un fuerte énfasis en la Arquitectura Hexagonal dentro del código fuente Java. A continuación, se presenta un desglose detallado del paquete `src/main/java/com/suresell/orders`.

### `src/main/java/com/suresell/orders`
Este paquete sirve como punto de entrada para el código fuente Java del microservicio, organizado de acuerdo con la Arquitectura Hexagonal.

#### `application`
Esta capa contiene la lógica específica de la aplicación, que orquesta las operaciones de dominio para cumplir con casos de uso específicos. Actúa como la "capa externa" del hexágono, traduciendo las solicitudes externas en acciones que interactúan con el núcleo del dominio.
-   **`dto`**:
    -   **Propósito**: **Objetos de Transferencia de Datos (DTOs)**. Estas clases están diseñadas para transferir datos entre diferentes capas de la aplicación (por ejemplo, de la capa web a la capa de aplicación, o entre microservicios). Definen la estructura de los datos para solicitudes, respuestas y transferencia de datos internos, aislando así el modelo de dominio de la exposición externa directa y de las preocupaciones específicas de la infraestructura.
    -   **Contenido**: `AdminActionRequest.java`, `ApplyDiscountCommand.java`, `ApplyDiscountResult.java`, `ClosurePreviewResponse.java`, `ClosureRequest.java`, `ClosureResponse.java`, `CreateCouponRequest.java`, `CreateDeliveryOrderRequest.java`, `DeliveryOrderResponse.java`, `LinkOrderCouponCommand.java`, `OrderItemDto.java`, `OrderItemRequestRecord.java`, `OrderItemResponseRecord.java`, `OrderRequestRecord.java`, `OrderResponseRecord.java`, `OrderSyncResponse.java`, `PagerAvailabilityDto.java`, `PagerAvailabilityResponse.java`, `PageResponse.java`, `ProductDiscountDto.java`, `ProductResponse.java`, `UpdateCouponRequest.java`.
-   **`usecase`**:
    -   **Propósito**: **Casos de Uso / Interactors**. Estas clases encapsulan los procesos de negocio específicos o las interacciones de usuario que la aplicación soporta. Orquestan el flujo de datos, aplican reglas de negocio a nivel de aplicación e interactúan con la capa de `domain` a través de sus puertos de entrada para lograr un objetivo de negocio particular. Cada caso de uso normalmente representa una característica o comando específico de la aplicación.
    -   **Contenido**: `DailyClosureHandler.java`, `DeliveryOrderHandler.java`, `DiscountHandler.java`, `OrderHandler.java`.

#### `domain`
Este es el corazón de la aplicación, completamente independiente de preocupaciones externas como bases de datos, frameworks o interfaces de usuario. Contiene la lógica central de negocio, entidades, objetos de valor y las interfaces (puertos) a través de las cuales interactúa con otras capas.
-   **`model`**:
    -   **Propósito**: Define las **entidades de negocio** y los **objetos de valor** principales. Estos son objetos Java simples (POJOs) que representan los conceptos fundamentales del negocio y aplican las reglas e invariantes específicas del dominio. Son independientes de la persistencia y agnósticos al framework.
    -   **Contenido**: `AppliesToType.java`, `CouponProduct.java`, `DailyClosure.java`, `DeliveryOrder.java`, `DeliveryStatus.java`, `DiscountCoupon.java`, `DiscountUsage.java`, `Order.java`, `OrderEditHistory.java`, `OrderItem.java`, `OrderStatus.java`, `PagerColor.java`.
-   **`port`**:
    -   **Propósito**: Define las **interfaces (puertos)** a través de las cuales la capa de dominio se comunica con el mundo exterior. Estos puertos son contratos abstractos que especifican *qué* necesita el dominio de sus actores externos o *qué* pueden pedir los actores externos al dominio.
    -   **`in`**:
        -   **Propósito**: **Puertos de Entrada (Inbound Ports)**. Estas son interfaces que la capa de `application` (casos de uso) utiliza para interactuar con el dominio. Representan las acciones o comandos que el dominio puede ejecutar, definiendo efectivamente la API de la capa de dominio. Las clases de `domain/service` suelen implementar estas interfaces.
        -   **Contenido**: `DailyClosurePort.java`, `DeliveryPort.java`, `DiscountPort.java`, `OrderPort.java`.
    -   **`out`**:
        -   **Propósito**: **Puertos de Salida (Outbound Ports)**. Estas son interfaces que la capa de `domain` utiliza para interactuar con sistemas externos (por ejemplo, bases de datos, otros microservicios, APIs externas). Especifican las capacidades que el dominio requiere de sus adaptadores "controlados" en la capa de `infrastructure`, como la persistencia de datos o las llamadas a servicios externos.
        -   **Contenido**: `CouponProductRepositoryPort.java`, `DailyClosureRepositoryPort.java`, `DeliveryOrderRepositoryPort.java`, `DiscountCouponRepositoryPort.java`, `DiscountUsageRepositoryPort.java`, `OrderEditHistoryRepositoryPort.java`, `OrderItemRepositoryPort.java`, `OrderRepositoryPort.java`, `ProductClientPort.java`.
-   **`service`**:
    -   **Propósito**: Contiene **servicios de dominio**. Estas clases encapsulan la lógica de negocio que no encaja naturalmente en una sola entidad u objeto de valor. A menudo coordinan múltiples objetos de dominio para realizar una operación compleja e implementan los puertos de entrada definidos en `domain/port/in`.
    -   **Contenido**: `DailyClosureDomainService.java`, `DeliveryOrderDomainService.java`, `OrderDomainService.java`.

#### `infrastructure`
Esta capa contiene los **adaptadores** que implementan los puertos definidos en la capa de `domain`. Se encarga de todas las preocupaciones externas, conectando el núcleo de la aplicación a bases de datos, frameworks web, servicios externos y otros detalles técnicos.
-   **`client`**:
    -   **Propósito**: Contiene componentes responsables de realizar llamadas a microservicios o APIs externas.
    -   **`adapter`**:
        -   **Propósito**: Estos son **adaptadores de salida (outbound adapters)** que implementan los puertos de salida (por ejemplo, `ProductClientPort`) definidos en la capa de `domain`. Manejan los detalles específicos de la comunicación con servicios externos, como llamadas a la API REST, interacciones con colas de mensajes, etc.
        -   **Contenido**: `ProductClientAdapter.java`.
-   **`config`**:
    -   **Propósito**: Alberga las **clases de configuración de Spring**. Estas clases definen beans, configuran varios aspectos del contexto de la aplicación Spring y manejan preocupaciones transversales como las políticas CORS, la configuración de la fuente de datos y las personalizaciones de `RestTemplate`.
    -   **Contenido**: `CorsConfig.java`, `DataSourceConfig.java`, `RestTemplateConfig.java`, `WebConfig.java`.
-   **`persistence`**:
    -   **Propósito**: Contiene **adaptadores para la persistencia de datos**. Estos componentes implementan los puertos de salida del repositorio (`*RepositoryPort`) de la capa de `domain`, traduciendo las operaciones de datos específicas del dominio en comandos específicos de la base de datos (por ejemplo, consultas JPA).
    -   **Contenido**: `CouponProductRepositoryAdapter.java`, `DailyClosureRepositoryAdapter.java`, `DeliveryOrderRepositoryAdapter.java`, `DiscountCouponRepositoryAdapter.java`, `DiscountUsageRepositoryAdapter.java`, `OrderEditHistoryRepositoryAdapter.java`, `OrderItemRepositoryAdapter.java`, `OrderRepositoryAdapter.java`. Cada `*RepositoryAdapter` implementa un `*RepositoryPort` correspondiente de `domain/port/out`.
    -   **`repository`**:
        -   **Propósito**: Contiene **interfaces de repositorio de Spring Data JPA**. Estas interfaces definen métodos estándar de acceso a datos (operaciones CRUD) para interactuar con la base de datos, extendiendo típicamente `JpaRepository`. Son utilizadas por los adaptadores de persistencia para realizar operaciones de base de datos.
        -   **Contenido**: `CouponProductJpaRepository.java`, `DailyClosureJpaRepository.java`, `DeliveryOrderJpaRepository.java`, `DiscountCouponJpaRepository.java`, `DiscountUsageJpaRepository.java`, `OrderEditHistoryJpaRepository.java`, `OrderItemJpaRepository.java`, `OrderJpaRepository.java`.
-   **`web`**:
    -   **Propósito**: Contiene los **controladores REST** y otros componentes relacionados con la web. Esto actúa como un **adaptador de entrada (inbound adapter)**, recibiendo solicitudes HTTP, traduciéndolas en comandos o consultas para la capa de `application` (casos de uso) y devolviendo respuestas HTTP apropiadas.
    -   **Contenido**: `DailyClosureController.java`, `DeliveryOrderController.java`, `DiscountController.java`, `HealthController.java`, `OrderController.java`.
    -   **`adapter`**:
        -   **Propósito**: Adaptadores específicos de la web que podrían manejar transformaciones de solicitud/respuesta, autenticación o lógica específica relacionada con la web antes de delegar a los controladores principales o a los casos de uso de la aplicación. Esto puede incluir el mapeo de cargas útiles de solicitudes web a objetos `application/dto`.
        -   **Contenido**: `OrderRequestWebAdapter.java`.

#### `shared`
Este paquete contiene utilidades comunes, enumeraciones, excepciones personalizadas y mapeadores de datos que se utilizan en las diferentes capas del microservicio, promoviendo la reutilización y la consistencia.
-   **`enums`**:
    -   **Propósito**: Enumeraciones (clases `enum`) utilizadas en toda la aplicación para definir un conjunto de valores constantes con nombre, mejorando la legibilidad del código y la seguridad de tipos para opciones predefinidas (por ejemplo, estados, tipos).
    -   **Contenido**: `AppliesToType.java`, `DeliveryStatus.java`, `OrderStatus.java`, `PagerColor.java`, `SyncErrorType.java`.
-   **`exception`**:
    -   **Propósito**: **Clases de excepción personalizadas** diseñadas para manejar condiciones de error específicas dentro del microservicio. Esto permite un manejo de errores más granular y una mejor comunicación de problemas específicos a los clientes u otros componentes internos. `GlobalExceptionHandler.java` centraliza el manejo de estas excepciones.
    -   **Contenido**: `AdminPasswordException.java`, `DeliveryExceptionHandler.java`, `GlobalExceptionHandler.java`, `MesaDuplicadaException.java`, `OrderAlreadyDeliveredException.java`, `OrderEditNotAllowedException.java`, `OrderIdAlreadyExistsException.java`, `OrderNotFoundException.java`, `PagerOcupadoException.java`.
-   **`export`**:
    -   **Propósito**: Contiene clases relacionadas con funcionalidades de exportación de datos, como la generación de informes en varios formatos.
    -   **Contenido**: `DailyClosureExcelExporter.java`.
-   **`mapper`**:
    -   **Propósito**: Clases de utilidad (a menudo utilizando bibliotecas como MapStruct) responsables de **mapear datos** entre diferentes tipos de objetos en las capas. Por ejemplo, mapear objetos `application/dto` a objetos `domain/model` o objetos `domain/model` a entidades de `infrastructure/persistence`, y viceversa.
    -   **Contenido**: `OrderMapper.java`.
