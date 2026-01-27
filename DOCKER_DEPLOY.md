# Guía de Despliegue Docker para AWS ECS

## Resumen

Este microservicio está preparado para desplegarse en **Amazon ECS** con **Application Load Balancer** usando contenedores Docker optimizados para producción.

## Características del Dockerfile

### Multi-Stage Build
- **Stage 1 (Builder)**: Compila el JAR usando Gradle 8.4 + JDK 17
- **Stage 2 (Runtime)**: Ejecuta la aplicación con Amazon Corretto 17 Alpine

### Optimizaciones

1. **Cache de dependencias**: Las dependencias de Gradle se descargan en una capa separada para aprovechar el cache de Docker
2. **Imagen optimizada**: Usa Amazon Corretto 17 Alpine (optimizado para AWS)
3. **Seguridad**:
   - Usuario no-root (`spring:spring`)
   - Imagen base oficial de Amazon Corretto
4. **Alta concurrencia**:
   - JVM optimizada para contenedores con `-XX:+UseContainerSupport`
   - G1GC para mejor manejo de memoria
   - MaxRAMPercentage=75% para aprovechar memoria del contenedor
5. **Signal handling**: Usa `dumb-init` para manejo correcto de señales SIGTERM

### Tamaño de la Imagen
- **Stage Builder**: ~800 MB (solo durante build, descartado después)
- **Stage Runtime**: ~730 MB (imagen final)

## Construcción de la Imagen

### Build básico
```bash
docker build -t ms-order-product:latest .
```

### Build con tag específico
```bash
docker build -t ms-order-product:v1.0.0 .
```

### Build para AWS ECR
```bash
# Autenticarse en ECR
aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin 654479365413.dkr.ecr.us-east-1.amazonaws.com

# Build con tag de ECR
docker build -t 654479365413.dkr.ecr.us-east-1.amazonaws.com/ms-order-product:latest .

# Push a ECR
docker push 654479365413.dkr.ecr.us-east-1.amazonaws.com/ms-order-product:latest
```

## Ejecución Local

### Ejecución básica
```bash
docker run -p 8081:8081 ms-order-product:latest
```

### Con variables de entorno
```bash
docker run -p 8081:8081 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/pos_db \
  -e SPRING_DATASOURCE_USERNAME=postgres \
  -e SPRING_DATASOURCE_PASSWORD=yourpassword \
  ms-order-product:latest
```

### Con JVM options personalizados
```bash
docker run -p 8081:8081 \
  -e JAVA_OPTS="-XX:MaxRAMPercentage=80.0 -Xlog:gc*:stdout" \
  ms-order-product:latest
```

### Modo detached con logs
```bash
# Iniciar contenedor
docker run -d --name ms-order-product -p 8081:8081 ms-order-product:latest

# Ver logs
docker logs -f ms-order-product

# Detener contenedor
docker stop ms-order-product

# Eliminar contenedor
docker rm ms-order-product
```

## Verificación

### Verificar que la aplicación está corriendo
```bash
curl http://localhost:8081
```

### Inspeccionar la imagen
```bash
# Ver capas de la imagen
docker history ms-order-product:latest

# Ver tamaño
docker images ms-order-product

# Inspeccionar configuración
docker inspect ms-order-product:latest
```

## Despliegue en AWS ECS

### 1. Crear repositorio en ECR (si no existe)
```bash
aws ecr create-repository \
  --repository-name ms-order-product \
  --region us-east-1
```

### 2. Push de la imagen
```bash
# Login en ECR
aws ecr get-login-password --region us-east-1 | \
  docker login --username AWS --password-stdin \
  654479365413.dkr.ecr.us-east-1.amazonaws.com

# Tag y push
docker tag ms-order-product:latest \
  654479365413.dkr.ecr.us-east-1.amazonaws.com/ms-order-product:latest

docker push 654479365413.dkr.ecr.us-east-1.amazonaws.com/ms-order-product:latest
```

### 3. Configuración de Task Definition (ECS)

Ejemplo de configuración de contenedor en la Task Definition:

```json
{
  "name": "ms-order-product",
  "image": "654479365413.dkr.ecr.us-east-1.amazonaws.com/ms-order-product:latest",
  "cpu": 512,
  "memory": 1024,
  "portMappings": [
    {
      "containerPort": 8081,
      "protocol": "tcp"
    }
  ],
  "environment": [
    {
      "name": "SPRING_DATASOURCE_URL",
      "value": "jdbc:postgresql://database-surresell.c740uem2quei.us-east-2.rds.amazonaws.com:5432/pos_db"
    }
  ],
  "secrets": [
    {
      "name": "SPRING_DATASOURCE_PASSWORD",
      "valueFrom": "arn:aws:secretsmanager:region:account:secret:db-password"
    }
  ],
  "logConfiguration": {
    "logDriver": "awslogs",
    "options": {
      "awslogs-group": "/ecs/ms-order-product",
      "awslogs-region": "us-east-1",
      "awslogs-stream-prefix": "ecs"
    }
  }
}
```

### 4. Configuración del Application Load Balancer

**Target Group Health Check:**
- Protocol: HTTP
- Path: `/` (o endpoint específico de tu API)
- Port: 8081
- Healthy threshold: 2
- Unhealthy threshold: 3
- Timeout: 5 seconds
- Interval: 30 seconds
- Success codes: 200

**Listener:**
- Protocol: HTTP o HTTPS
- Port: 80 o 443
- Forward to: ms-order-product target group

## Variables de Entorno Recomendadas

```bash
# Database
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD

# JVM Options (ya configuradas por defecto)
JAVA_OPTS

# Spring Profile (opcional)
SPRING_PROFILES_ACTIVE=production

# Logging
LOGGING_LEVEL_ROOT=INFO
LOGGING_LEVEL_COM_SURESELL=DEBUG
```

## Monitoreo

### Logs en ECS
```bash
# Ver logs del servicio
aws logs tail /ecs/ms-order-product --follow
```

### Métricas importantes
- CPU utilization
- Memory utilization
- Request count (ALB)
- Target response time (ALB)
- Healthy/Unhealthy host count

## Troubleshooting

### El contenedor no inicia
```bash
# Ver logs del contenedor
docker logs <container-id>

# Ejecutar shell en el contenedor
docker run -it --entrypoint /bin/sh ms-order-product:latest
```

### Problemas de memoria
```bash
# Ajustar MaxRAMPercentage
docker run -e JAVA_OPTS="-XX:MaxRAMPercentage=60.0" ms-order-product:latest
```

### Problemas de conectividad a RDS
- Verificar Security Groups
- Verificar que el ECS task tenga acceso a la VPC correcta
- Verificar credenciales de base de datos

## Notas Importantes

1. **Secrets Management**: Usa AWS Secrets Manager o Parameter Store para credenciales sensibles
2. **Health Checks**: Configura health checks en el ALB Target Group, no en el Dockerfile
3. **Scaling**: ECS permite auto-scaling basado en CPU, memoria o métricas de ALB
4. **Blue/Green Deployments**: Considera usar AWS CodeDeploy para deployments sin downtime
5. **Recursos**: Ajusta CPU y memoria según la carga esperada (recomendado: 512 CPU, 1024 MB para inicio)
