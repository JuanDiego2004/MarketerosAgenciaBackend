# Backend Spring Boot en Railway — Java 25

El Dockerfile de dos etapas fija Java 25 y ejecuta el JAR con un usuario no privilegiado. PORT lo asigna Railway. No se incluyen archivos .env en la imagen; las credenciales se añaden como variables del servicio.

## Servicio

1. Vincular el repositorio backend_agencia al servicio existente de Railway. Si es un monorepo, elegir backend_agencia como Root Directory.
2. Seleccionar Dockerfile (se detecta en la raíz del backend). No se necesita sobrescribir Start Command.
3. Configurar Healthcheck Path: /actuator/health, timeout: 300 segundos. Ese endpoint comprueba la disponibilidad de la base y no devuelve detalles internos.
4. Añadir las variables de .env.example y .env.crm.example a Variables del servicio, con los valores propios.
5. Publicar un dominio HTTPS del servicio y establecer CRM_PUBLIC_URL=https://dominio-publico-del-backend, sin una ruta /api adicional.
6. APP_CORS_ALLOWED_ORIGINS debe contener los orígenes exactos del frontend, separados por comas. Configurar VITE_API_URL con el dominio HTTPS del backend al compilar el frontend.
7. Desplegar y comprobar /actuator/health (200) y /swagger-ui/index.html. Una API CRM sin token debe rechazar el acceso.

Las migraciones CRM son manuales y no se ejecutan en cada arranque. Railway debe apuntar a la base que corresponde al entorno. Desactivar CRM_AUTOMATIZACIONES_HABILITADAS hasta terminar la prueba de proveedores.

## Variables mínimas

DB_URL (JDBC PostgreSQL), DB_USERNAME, DB_PASSWORD, SUPABASE_URL, SUPABASE_PUBLISHABLE_KEY, APP_CORS_ALLOWED_ORIGINS. Los valores SMTP, credenciales de WhatsApp y clave Mockaroo se toman de Variables, no se hornean en Docker.

## Reversión

Deshabilitar automatizaciones y envíos antes de revertir a una versión anterior que no conozca las supresiones. Revertir el deployment desde Railway sin borrar tablas. La base debe conservar las migraciones aditivas y los datos.

Referencias oficiales: [Dockerfiles](https://docs.railway.com/guides/dockerfiles), [Spring Boot](https://railway.com/guides/spring-boot). El despliegue real requiere acceso al proyecto/servicio del usuario; crear estos archivos no significa que ya esté publicado.

## Rendimiento publicitario vigente

La publicidad usa Mockaroo (`CRM_MOCKAROO_HABILITADO=true`, `CRM_MOCKAROO_API_KEY`). Aplicar migraciones hasta v8 antes de desplegar; no son automáticas. Los conectores Meta Ads, Google Ads y GA4 se retiraron, conservando históricos. WhatsApp Cloud permanece con sus credenciales y `CRM_WHATSAPP_VERSION`. Actualizar también el frontend, que utiliza `/api/crm/rendimiento`. La interfaz muestra inversión y retorno de oportunidades ganadas del CRM, separados de las conversiones de plataforma.
