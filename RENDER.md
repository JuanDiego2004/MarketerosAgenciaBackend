# Backend en Render con Docker y Java 25

El Dockerfile compila el JAR con JDK 25 y lo ejecuta con JRE 25 y usuario sin privilegios. La imagen no contiene `.env`, Git, archivos del IDE, pruebas o salidas de compilaciones anteriores. No se ejecutan pruebas contra la base durante el build.

Spring Boot ya escucha en `0.0.0.0` y toma el puerto de `PORT`. Render usa 10000 por defecto. El endpoint público `/actuator/health` comprueba la base de datos y no muestra detalles internos. El Dockerfile también puede seguir utilizándose en Railway, cuyo `PORT` sobrescribe el valor de la imagen.

## Despliegue desde el panel

1. Subir los archivos del backend a tu repositorio Git, incluyendo `Dockerfile`, `.dockerignore`, `.mvn`, `mvnw`, `pom.xml` y `src/main`. No subir `.env`.
2. En Render elegir **New → Web Service** y conectar el repositorio y la rama que corresponden.
3. Configurar:

   | Campo | Valor |
   | --- | --- |
   | Language / Runtime | Docker |
   | Root Directory | Vacío si el repositorio contiene directamente este backend; `backend_agencia` solo si realmente es una subcarpeta de un monorepo |
   | Dockerfile Path | `./Dockerfile`, relativo a la raíz elegida |
   | Docker Build Context | `.` |
   | Docker Command | Vacío: se usa el ENTRYPOINT de la imagen |
   | Health Check Path | `/actuator/health` |

   No añadir comandos Maven o `java -jar` en los campos de un runtime nativo; Docker ya contiene la compilación y el arranque.
4. Elegir el plan conscientemente. El Blueprint incluido usa **Free** para que no seleccione un plan de pago implícitamente. Sus límites se explican más abajo.
5. En **Environment**, añadir los valores reales de las variables de `.env.example`. Las credenciales se introducen en Render, sin pegarlas en el chat o en archivos versionados.
6. Desplegar y comprobar que `/actuator/health` devuelve HTTP 200 con `status: UP`, y que `/swagger-ui/index.html` carga. Una API CRM sin sesión debe devolver 401.

## Variables

Para ejecutar el backend localmente también se necesitan `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` en el archivo `.env` de este backend (o en el entorno de ejecución del IDE). Las variables de Render no se copian al equipo. `DB_PASSWORD` es la contraseña de PostgreSQL del mismo proyecto; la clave publicable de Supabase no la reemplaza. El mensaje SCRAM «no password was provided» indica que falta esa configuración. Después de añadirla, reiniciar el backend. No versionar `.env` ni compartir su contraseña.

| Variable | Valor esperado |
| --- | --- |
| `DB_URL` | URL JDBC de la base PostgreSQL existente, por ejemplo `jdbc:postgresql://HOST:6543/postgres?sslmode=require` |
| `DB_USERNAME` | Usuario de esa base; en el pooler de Supabase suele incluir la referencia del proyecto |
| `DB_PASSWORD` | Contraseña de la base |
| `SUPABASE_URL` | URL del mismo proyecto de autenticación que utiliza el frontend |
| `SUPABASE_PUBLISHABLE_KEY` | Clave pública/publicable del proyecto que requiere el backend; no usar la contraseña de la base |
| `APP_CORS_ALLOWED_ORIGINS` | `https://marketerosfrontend.onrender.com,http://localhost:5173,http://localhost:3000`. Sin rutas ni barra final |
| `PORT` | Render lo define; el Blueprint utiliza 10000 |
| `CRM_PUBLIC_URL` | Dominio HTTPS real del nuevo servicio de Render, sin `/api` al final |
| `CRM_AUTOMATIZACIONES_HABILITADAS` | Mantener `false` durante configuración y pruebas |
| `BREVO_API_KEY` | Clave API de Brevo (`xkeysib-...`); mantenerla solo en el backend |
| `BREVO_API_URL` | `https://api.brevo.com/v3/smtp/email` para envíos transaccionales |
| `CRM_SMTP_FROM` | Dirección remitente registrada y verificada en Brevo |
| `CRM_EMAIL_LOGO_URL` | (Opcional) URL HTTPS pública del logo para la plantilla; por defecto `https://marketerosfrontend.onrender.com/email-logo.svg` |

Después de conocer el dominio del servicio, establecer `CRM_PUBLIC_URL` con esa dirección y volver a desplegar/aplicar las variables. Actualizar cualquier valor anterior que apuntara a Railway o localhost.

Para Brevo, crear una **API key** en **SMTP & API → API keys** (comienza con `xkeysib-`; la SMTP key `xsmtpsib-` no sirve para esta integración) y registrar/verificar el remitente en **Senders & IPs**. En Render, configurar `BREVO_API_KEY` con esa clave y `CRM_SMTP_FROM` con el correo verificado. No colocar la clave en el frontend ni en una variable `VITE_*`.

Las variables de Mockaroo, WhatsApp Cloud y Brevo son opcionales para arrancar, pero necesarias para sus respectivas funciones. El correo utiliza Brevo Email API y WhatsApp Cloud es el único canal de mensajería. Los conectores de publicidad Meta, Google Ads y GA4 se retiraron; no requieren credenciales en esta versión. Copiar únicamente las que se vayan a utilizar desde `.env.crm.example`. No se incluyen en la imagen ni en el Blueprint. Los estados «configurado» no prueban la validez de la cuenta del proveedor.

La base de datos debe tener aplicadas las migraciones correspondientes. No se ejecutan automáticamente al arrancar ni al construir la imagen. La base configurada en esta sesión ya tiene CRM v1–v8; si Render utiliza otra base, inventariarla y aplicar las migraciones antes de arrancar. No ejecutar todos los scripts de nuevo sobre tablas existentes.

## Blueprint opcional

En local, Spring Boot carga las variables del archivo `.env` situado junto al `pom.xml`. En Render, agrega los secretos en **Environment** del servicio y vuelve a desplegar; no se leen archivos `.env` locales en Render.

En **Environment** del backend en Render, establecer `APP_CORS_ALLOWED_ORIGINS=https://marketerosfrontend.onrender.com,http://localhost:5173,http://localhost:3000` y guardar/desplegar. Esto permite el frontend publicado y el desarrollo local. Si existe una variable anterior, reemplazarla completa: los valores del entorno prevalecen sobre los predeterminados del código. Los orígenes deben coincidir con el protocolo, host y puerto del navegador; no añadas rutas ni barra final. Las pruebas `CorsRenderTests` cubren preflight de login/CRM, peticiones públicas, localhost, rechazo de otros dominios y autenticación conservada.

El frontend utiliza `VITE_API_URL=https://marketerosagenciabackend.onrender.com`. Su Static Site requiere una regla Rewrite de `/*` a `/index.html` para poder recargar rutas como `/login` y `/contratos`; se preparó también `MarketingSystem/render.yaml`.

`render.yaml` permite crear el servicio desde **New → Blueprint**. Está preparado para un repositorio cuya raíz es el backend. Los valores `sync: false` se solicitan en Render durante la creación inicial; las integraciones opcionales se añaden después desde Environment.

Si el backend está dentro de un monorepo, adaptar `dockerfilePath` y `dockerContext` a la ubicación real desde la raíz del repositorio, o usar el despliegue manual anterior. No copiar `render.yaml` a la raíz de un repositorio diferente sin revisar esas rutas.

## WhatsApp y frontend

Una vez que el servicio esté operativo:

- Añadir `CRM_WHATSAPP_APP_SECRET`, `CRM_WHATSAPP_VERIFY_TOKEN`, `CRM_WHATSAPP_PHONE_ID` y `CRM_WHATSAPP_TOKEN` con los valores de la misma app y número configurados en Meta.
- Configurar la URL de devolución de Meta con el dominio real del servicio seguido de `/api/webhooks/whatsapp`.
- Usar el mismo valor de `CRM_WHATSAPP_VERIFY_TOKEN` en Meta, verificar y suscribir `messages`. Guía completa: `MarketingSystem/docs/WHATSAPP_WEBHOOK.md`.
- Cambiar `VITE_API_URL` en el frontend al dominio HTTPS del backend, sin `/api`, y reiniciar el frontend o recompilarlo si ya está publicado. Incluir el origen de ese frontend en `APP_CORS_ALLOWED_ORIGINS`.

## Límites del plan Free

Render suspende los servicios gratuitos después de 15 minutos sin tráfico entrante. El arranque en frío puede retrasar la recepción del webhook; las tareas programadas no se ejecutan mientras el servicio está suspendido.

El servicio gratuito se suspende después de 15 minutos sin tráfico. Los envíos de correo usan la API HTTPS de Brevo, así que no dependen de puertos SMTP.

## Prueba Docker local

Con Docker Desktop instalado y el motor Linux iniciado, desde la raíz del backend:

```powershell
docker build -t marketing-crm-backend:local .
docker run --rm --name marketing-crm-backend -p 8080:10000 --env-file .env -e PORT=10000 -e CRM_AUTOMATIZACIONES_HABILITADAS=false marketing-crm-backend:local
```

El puerto local 8080 debe estar libre. Comprobar después `http://localhost:8080/actuator/health` y `http://localhost:8080/swagger-ui/index.html`. Para probar la recepción pública del webhook se necesita el dominio HTTPS de Render o un túnel HTTPS de desarrollo; localhost no es accesible desde Meta.

## Reversión

Desactivar automatizaciones y la suscripción del webhook antes de volver a un backend que no soporte el nuevo contrato. Mantener la base y sus tablas; revertir el despliegue desde Render no implica borrar datos.

## Referencias

Verificación local: `mvnw -DskipTests package` completado con Java 25; `render.yaml` analizado correctamente con SnakeYAML; cambios sin errores de espacios en `git diff --check`. No se ejecutó `docker build` porque este entorno no tiene Docker/Podman disponibles. No se desplegó ni creó ningún servicio en Render.

[Docker en Render](https://render.com/docs/docker), [puerto y dirección de escucha](https://render.com/docs/web-services#port-binding), [comprobaciones de salud](https://render.com/docs/health-checks), [Blueprint](https://render.com/docs/blueprint-spec), [límites del plan gratuito](https://render.com/docs/free).
# Rendimiento publicitario con Mockaroo

En **Environment**, configurar `CRM_MOCKAROO_HABILITADO=true` y `CRM_MOCKAROO_API_KEY` con la clave privada de Mockaroo. La clave permanece en el backend. El `.env` local no configura Render.

Desplegar el backend y el frontend actualizados. Aplicar `src/main/resources/sql/crm-v7.sql` y `crm-v8.sql` después de las migraciones anteriores, o el auxiliar `scripts/PrepararCrm.java --aplicar`. Docker no aplica migraciones al arrancar. Las migraciones añaden estadísticas e inversiones con RLS y conservan todos los datos anteriores.

La pantalla se encuentra en **Métricas → Rendimiento publicitario**, con **Fuente: Mockaroo**. Hasta 90 días y 9 países por campaña: 810 filas por descarga, con reutilización de días guardados. No se requiere publicar anuncios ni conectar Meta/TikTok/YouTube para este flujo. Eliminar las variables o establecer `CRM_MOCKAROO_HABILITADO=false` conserva el histórico y bloquea nuevas descargas.


WhatsApp conserva su webhook y sus credenciales. Configurar `CRM_WHATSAPP_VERSION` con la versión que usa el servicio; esta variable sustituye la versión compartida de Meta. No retirar los secretos de WhatsApp al limpiar variables antiguas de anuncios. Los gastos adicionales y los ingresos de oportunidades ganadas alimentan el retorno comercial; revisar `MarketingSystem/docs/MODELO_PRODUCTO_MARKETING.md`.

## Unificación de empresas CRM v9

Antes de publicar esta versión, aplicar las migraciones pendientes hasta `src/main/resources/sql/crm-v9.sql` con el auxiliar `scripts/PrepararCrm.java --aplicar`. La base configurada en este workspace ya tiene v9; si el servicio usa otra base, preparar allí los grupos pendientes. Se conserva la tabla cliente y se vincula automáticamente su perfil CRM; no se borran campañas, contactos ni adjuntos. Los registros anteriores se respaldan en crm_empresa_migracion. Actualizar backend y frontend para utilizar Clientes / Empresas (`/empresas`); el contrato `/api/crm/cuentas` sigue disponible. Difusión se separa en `/canales-difusion`; WhatsApp y los demás canales conservan `/integraciones`. Ver `MarketingSystem/docs/EMPRESAS_UNIFICADAS.md`.
## Contratos y servicios (CRM v10)

### Entregas y portal (CRM v11)

La actualización posterior requiere también `crm-v11.sql`. Añade alcance contractual, multimedia privada en PostgreSQL, PDF versionados, acceso por empresa y aprobación del cliente. La base del workspace ya tiene v11. Si Render usa otra, aplicar con PrepararCrm antes del despliegue. Multipart acepta hasta 20 MB por archivo; los adjuntos antiguos mantienen sus límites propios. No se necesita un bucket público ni tokens nuevos. El rol Cliente inicia sesión mediante el sistema existente y se vincula a su empresa desde Configuración → Accesos al portal. Actualizar frontend y backend Java 25 juntos. No desplegado automáticamente.

Antes del backend actualizado aplicar `src/main/resources/sql/crm-v10.sql` mediante el auxiliar `scripts/PrepararCrm.java --aplicar` con Java 25 y el driver PostgreSQL. Añade siete tablas con RLS, conserva históricos y habilita los tres servicios. La base configurada en el workspace ya recibió v10; comprobar si Render usa esa misma base. Docker no ejecuta las migraciones automáticamente. Publicar también el frontend para las rutas Contratos, Cotizaciones, Anuncios, Calendario y Entregables. No se necesitan nuevas credenciales; Mockaroo utiliza las variables existentes y las comunicaciones conservan las suyas.
# Contratación contextual (v12–v13)

Ejecutar `src/main/resources/sql/crm-v12.sql` y después `crm-v13.sql` sobre una base que ya tenga v1–v11. Incorporan borradores con archivos, ficha contextual y ampliaciones del mismo contrato. Se aplicaron en la base configurada durante esta corrección; no requieren nuevas credenciales de Meta. Recompilar y reiniciar el backend Java 25 junto con el frontend actualizado. Los datos y servicios de comunicación existentes se conservan.
