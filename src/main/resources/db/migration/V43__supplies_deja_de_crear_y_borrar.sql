-- =====================================================================
-- V43 -- `supplies` deja de crear y de borrar insumos.
--
-- EL PROBLEMA, CON UN CASO REAL DEL MISMO DIA
--
-- Desde que existe `inventario.insumos` hay DOS listas de insumos y ninguna
-- manda sobre la otra. El 2026-09-01 alguien borro dos insumos de prueba
-- —`aaa` y `test2`— desde la pantalla vieja. **Siguen vivos en la nueva.**
--
-- Paso en las primeras horas de convivencia. Cada dia que las dos aceptan
-- escrituras es un dia de divergencia que despues hay que reconciliar a mano, y
-- quien la produce no tiene forma de enterarse.
--
-- POR QUE ESTO Y NO "SOLO LECTURA"
--
-- Dejar `supplies` entera en solo lectura habria quitado capacidad que todavia
-- se usa. Medido antes de decidir: de los 96 insumos, **4 tienen stock y 2
-- tienen alerta de minimo**. Es poco, pero no es cero, y romperlo no aporta
-- nada.
--
-- Lo que de verdad divergia es la IDENTIDAD:
--
--   INSERT  -> nace un insumo que el sistema nuevo no conoce
--   DELETE  -> desaparece uno que el sistema nuevo sigue teniendo
--   cambiar `name` o `unit` -> el mismo insumo pasa a llamarse o medirse
--                              distinto en cada lado
--
-- El stock y el minimo no divergen: son datos operativos del modulo viejo que a
-- nadie mas le importan. Se dejan pasar.
--
-- 🔴 SE HACE CON UN TRIGGER Y NO CON UN REVOKE
--
-- Un `REVOKE` daria "permission denied for table supplies", que el panel
-- convierte en un 500 y la persona lee como "se rompio". Un trigger puede
-- **decir que hacer en su lugar**, que es la diferencia entre una puerta
-- cerrada y una puerta con un cartel.
--
-- REVERSIBLE: `DROP TRIGGER trg_supplies_sin_identidad ON public.supplies;`
-- =====================================================================

CREATE OR REPLACE FUNCTION public.fn_supplies_sin_identidad()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        RAISE EXCEPTION
            'Los insumos nuevos se crean en "Insumos y Compras". Si se crea aqui, '
            'el costeo no lo ve y las dos listas dejan de coincidir.';
    END IF;

    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION
            'Un insumo no se borra aqui: seguiria existiendo en "Insumos y Compras". '
            'Alli se puede fusionar con otro o darlo de baja, y queda el rastro.';
    END IF;

    -- UPDATE: solo se frena lo que define QUE es la cosa.
    IF (NEW.name IS DISTINCT FROM OLD.name) THEN
        RAISE EXCEPTION
            'El nombre se cambia en "Insumos y Compras". Cambiarlo aqui dejaria el '
            'mismo insumo con dos nombres distintos, uno en cada lista.';
    END IF;

    IF (NEW.unit IS DISTINCT FROM OLD.unit) THEN
        RAISE EXCEPTION
            'La unidad se declara en "Insumos y Compras", que es donde se convierte '
            'a costo. Cambiarla aqui no cambia ningun calculo y las descuadra.';
    END IF;

    -- Stock, minimo, precio y categoria siguen siendo del modulo viejo.
    RETURN NEW;
END
$$;

DROP TRIGGER IF EXISTS trg_supplies_sin_identidad ON public.supplies;
CREATE TRIGGER trg_supplies_sin_identidad
    BEFORE INSERT OR UPDATE OR DELETE ON public.supplies
    FOR EACH ROW
    EXECUTE FUNCTION public.fn_supplies_sin_identidad();

COMMENT ON FUNCTION public.fn_supplies_sin_identidad IS
    'La identidad de un insumo vive en inventario.insumos. Aqui solo se pueden '
    'seguir tocando stock, minimo, precio y categoria.';


DO $cierre$
DECLARE fallo TEXT;
BEGIN
    -- Que el trigger exista no dice nada: hay que ver que HAGA algo. Se prueba
    -- contra la propia tabla, dentro de la migracion, y se deshace.
    BEGIN
        INSERT INTO public.supplies (tenant_id, name, unit)
        VALUES ('__prueba_de_v43__', 'no deberia entrar', 'Unidad');
        fallo := 'el trigger dejo pasar un INSERT';
    EXCEPTION WHEN OTHERS THEN
        NULL;  -- lo esperado
    END;

    IF fallo IS NOT NULL THEN
        RAISE EXCEPTION 'V43: %', fallo;
    END IF;

    IF EXISTS (SELECT 1 FROM public.supplies WHERE tenant_id = '__prueba_de_v43__') THEN
        RAISE EXCEPTION 'V43: la fila de prueba entro de verdad';
    END IF;

    RAISE NOTICE 'V43: supplies ya no crea ni borra insumos; stock y minimo siguen.';
END $cierre$;
