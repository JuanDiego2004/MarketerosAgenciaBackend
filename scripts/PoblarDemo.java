import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Datos de demostración: inventario por defecto, --comprobar revierte y --aplicar confirma. */
class PoblarDemo {
    static Map<String,String> configuracion() throws Exception {
        var config = new HashMap<>(System.getenv());
        if (Files.exists(Path.of(".env"))) for (String linea : Files.readAllLines(Path.of(".env"))) {
            if (linea.isBlank() || linea.stripLeading().startsWith("#") || !linea.contains("=")) continue;
            int n = linea.indexOf('=');
            String valor = linea.substring(n + 1).trim();
            if (valor.length() > 1 && ((valor.startsWith("\"") && valor.endsWith("\"")) || (valor.startsWith("'") && valor.endsWith("'")))) valor = valor.substring(1, valor.length() - 1);
            config.putIfAbsent(linea.substring(0, n).trim(), valor);
        }
        return config;
    }

    public static void main(String[] args) throws Exception {
        var config = configuracion();
        String url = config.get("DB_URL");
        if (url == null || !url.startsWith("jdbc:postgresql:")) throw new IllegalStateException("Falta la configuración PostgreSQL del backend");
        var props = new Properties();
        props.setProperty("user", config.getOrDefault("DB_USERNAME", ""));
        props.setProperty("password", config.getOrDefault("DB_PASSWORD", ""));
        props.setProperty("connectTimeout", "15"); props.setProperty("socketTimeout", "90"); props.setProperty("prepareThreshold", "0");
        try (var c = DriverManager.getConnection(url, props); var s = c.createStatement()) {
            s.setQueryTimeout(80);
            if (List.of(args).contains("--verificar")) {
                c.setReadOnly(true);
                try (var r=s.executeQuery(Files.readString(Path.of("scripts/verificar-demo-10.sql")))) {
                    while (r.next()) System.out.println(r.getString(1)+": "+r.getLong(2));
                }
                try (var r=s.executeQuery("select count(*) from public.usuario u where (u.correo like 'demo10.%@example.invalid' or u.id_usuario in(select r.id from public.crm_empresa_migracion m cross join lateral jsonb_to_recordset(m.datos->'registros') r(modulo text,lote int,id bigint) where m.tipo='lote_marketing_20261124' and m.registro_id=0 and r.modulo in('Trabajadores de prueba','Perfiles de cliente de prueba'))) and (u.id_supabase is not null or u.password_hash is not null or u.estado<>'INACTIVO')")) {
                    r.next(); if (r.getLong(1)!=0) throw new IllegalStateException("Revisa los accesos de prueba");
                }
                System.out.println("Verificación independiente terminada; conexión de solo lectura.");
                return;
            }
            if (!List.of(args).contains("--aplicar") && !List.of(args).contains("--comprobar")) {
                c.setReadOnly(true);
                try (var r = s.executeQuery("select table_name,column_name,data_type,is_nullable,column_default from information_schema.columns where table_schema='public' order by table_name,ordinal_position")) {
                    while (r.next()) System.out.printf("%s.%s %s null=%s default=%s%n", r.getString(1),r.getString(2),r.getString(3),r.getString(4),r.getString(5));
                }
                try (var r = s.executeQuery("select conrelid::regclass,conname,pg_get_constraintdef(oid) from pg_constraint where connamespace='public'::regnamespace and contype in ('c','u') order by conrelid::regclass::text,conname")) {
                    while (r.next()) System.out.println("REGLA " + r.getString(1)+" "+r.getString(2)+" "+r.getString(3));
                }
                try (var r = s.executeQuery("select r.nombre_rol,count(u.id_usuario) from public.rol r left join public.usuario u using(id_rol) group by r.id_rol order by r.id_rol")) {
                    while (r.next()) System.out.println("ROL " + r.getString(1) + ": " + r.getLong(2) + " usuarios");
                }
                System.out.println("Inventario de solo lectura terminado.");
                return;
            }
            c.setAutoCommit(false);
            try {
                s.execute("set local lock_timeout='10s'");
                s.execute("set local statement_timeout='75s'");
                if (List.of(args).contains("--normalizar")) {
                    s.execute(Files.readString(Path.of("scripts/normalizar-registros-marketing.sql")));
                    try (var r=s.executeQuery("select modulo,registros from normalizacion_resumen order by modulo")) {
                        while(r.next()) System.out.println(r.getString(1)+": "+r.getLong(2));
                    }
                    if (List.of(args).contains("--aplicar")) { c.commit(); System.out.println("CONFIRMADO: presentación actualizada; se conservaron IDs, métricas y accesos."); }
                    else { c.rollback(); System.out.println("COMPROBADO y REVERTIDO: presentación validada sin guardar cambios."); }
                    return;
                }
                boolean faltaGeografia;
                try (var r=s.executeQuery("select to_regclass('public.marketing_ubicacion') is null or to_regclass('public.marketing_cobertura') is null or to_regclass('public.marketing_resultado_geografico') is null")) { r.next(); faltaGeografia=r.getBoolean(1); }
                if (faltaGeografia) {
                    String migracion=Files.readString(Path.of("src/main/resources/sql/dashboard-v1.sql"))
                        .replaceFirst("(?m)^BEGIN;\\s*$", "").replaceFirst("(?m)^COMMIT;\\s*$", "");
                    s.execute(migracion);
                    System.out.println("Geografía: migración existente preparada dentro de la misma transacción.");
                }
                s.execute(Files.readString(Path.of("scripts/metricas-demo-mockaroo.sql")));
                String sql = Files.readString(Path.of("scripts/poblar-demo-10.sql"));
                s.execute(sql);
                s.execute(Files.readString(Path.of("scripts/normalizar-registros-marketing.sql")));
                try (var r = s.executeQuery("select modulo,registros from pg_temp.demo_resumen order by modulo")) {
                    while (r.next()) System.out.println(r.getString(1) + ": " + r.getLong(2));
                }
                if (List.of(args).contains("--aplicar")) { c.commit(); System.out.println("CONFIRMADO: registros DEMO guardados. Sin envíos, publicaciones ni cambios de acceso real."); }
                else { c.rollback(); System.out.println("COMPROBADO y REVERTIDO: no se guardaron registros (las secuencias pueden dejar huecos)."); }
            } catch (Exception e) { c.rollback(); throw e; }
        } catch (SQLException e) {
            System.err.println("Operación PostgreSQL revertida. SQLSTATE=" + e.getSQLState());
            if (e.getSQLState() != null && !e.getSQLState().startsWith("08") && !e.getSQLState().startsWith("28")) System.err.println(e.getMessage());
            System.exit(1);
        }
    }
}
