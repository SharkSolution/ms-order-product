package com.suresell.orders.infrastructure.config;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
@Configuration
public class DataSourceConfig {
    @Bean(name = "localDataSourceProperties")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource")
    public DataSourceProperties localDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * El pool de la aplicación.
     *
     * <h3>🔴 La anotación {@code @ConfigurationProperties} de abajo NO es
     * decorativa: sin ella el bloque {@code spring.datasource.hikari} entero se
     * ignora en silencio.</h3>
     *
     * {@code initializeDataSourceBuilder()} solo enlaza cuatro propiedades:
     * {@code url}, {@code username}, {@code password} y {@code driver-class-name}.
     * Todo lo del pool —tamaño, tiempos de vida, pulso de vida— lo enlaza esa
     * anotación, que Spring Boot pone en <i>su</i> {@code @Bean} de
     * {@code DataSourceConfiguration.Hikari}. Al declarar aquí un
     * {@code dataSource} propio y {@code @Primary}, la autoconfiguración se
     * aparta y la anotación se va con ella.
     *
     * <h3>Qué costó descubrirlo</h3>
     *
     * Medido en Producción el 2026-08-25 entre las 23:21 y las 23:29: dos
     * peticiones seguidas devolvieron 500 en el login. Cada una gastó 30 s
     * probando conexiones muertas, seis validaciones de 5,000 s clavados.
     *
     * <p>Los cinco números observados eran los <b>valores por defecto de
     * HikariCP</b>, no los del YAML:
     *
     * <pre>
     *   observado                         defecto     el YAML decía
     *   9 ociosas tras 67 min sin trafico  minIdle=10  minimum-idle: 1
     *   cero pulsos del housekeeper        keepalive=0 keepalive-time: 120000
     *   5,00 s por validacion              5000        —
     *   30 s hasta rendirse                30000       —
     *   conexiones podridas                30 min      max-lifetime: 900000
     * </pre>
     *
     * La víspera se había editado {@code application-cloud.yml} para arreglar
     * justo esto. No cambió nada, porque <b>nadie leía ese fichero.</b> El
     * despliegue salió verde, el fichero decía lo correcto, y el fallo siguió
     * intacto.
     *
     * <h3>Qué vería si esto estuviera roto</h3>
     *
     * Exactamente lo mismo que se veía: un servicio sano que devuelve 500 en la
     * primera petición tras un rato de silencio —la primera venta de la mañana—
     * y funciona al reintentar. Por eso la comprobación no puede ser leer el
     * YAML ni ver el despliegue en verde: hay que preguntarle al pool vivo qué
     * valores tiene. Eso hace {@code ConfiguracionDelPoolTest}.
     *
     * <p>El tipo declarado se queda en {@code DataSource} a propósito:
     * {@code TenantDataSourceConfig} envuelve este bean en un
     * {@code TenantAwareDataSource}, que no es un {@code HikariDataSource}.
     * Declarar el tipo concreto rompería ese envoltorio.
     */
    @Bean(name = "dataSource")
    @Primary
    @ConfigurationProperties(prefix = "spring.datasource.hikari")
    public DataSource localDataSource(@Qualifier("localDataSourceProperties") DataSourceProperties localDataSourceProperties) {
        return localDataSourceProperties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
