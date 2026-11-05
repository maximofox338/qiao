> **Propósito:** versión en español de la propuesta de colaboración (合作方案) para una empresa socia china, para que el fundador la revise antes de enviar el texto en chino.

**BORRADOR para discusión · No es una oferta vinculante · Todo lo legal y tributario es nuestra lectura preliminar y debe revisarlo un abogado habilitado en la RPC y en Hong Kong, y un asesor tributario chino, antes de usarlo como base.**

> **Nota para el fundador.** El documento que se envía es `socio-presentacion.zh-CN.md`; este es su equivalente fiel. Está escrito como lo redactaría un profesional chino de desarrollo de negocios: directo, con tablas, sin superlativos. Las cifras de comisión del socio son una **propuesta para negociar**, no un estándar de mercado verificado (ver §4.1). Antes de mandarlo, completá los marcadores `[ ]` y confirmá el tipo de cambio supuesto.

# Qiao (桥) × [Empresa socia]: propuesta de colaboración (borrador)

Versión: noviembre 2026 · Emisor: [Hong Kong Ltd] (en constitución) · Contacto: [email comercial genérico]

---

## 1. Resumen

1. **Qué es Qiao.** Una plataforma de **servicios técnicos por proyecto**, operada por una sociedad de Hong Kong (a constituir), en la que empresas extranjeras (prioridad: Asia-Pacífico, Golfo y Europa) contratan por **hitos** servicios de desarrollo de software y técnicos de ingenieros y equipos STEM de China continental. Sólo hay proyectos con entregables, hitos y fecha de fin (máximo 12 meses). **No se publican ofertas de empleo ni se crea relación laboral.**
2. **El rol que proponemos para ustedes.** Ser el **comprador y vendedor real del servicio**: por un lado, contratar en nombre propio a cada ingeniero con un acuerdo por entregables, pagarle mensualmente en RMB y retener el impuesto a la renta (IIT); por el otro, vender en nombre propio a la sociedad de Hong Kong un servicio de exportación de software y servicios técnicos, y facturarlo. No son un "canal" que cobra y reenvía dinero.
3. **Primero se cobra, después se paga: no adelantan fondos.** El cliente extranjero prepaga cada hito a la sociedad de Hong Kong antes de que empiece el trabajo. Aceptado el hito, la sociedad de Hong Kong paga mensualmente a la socia, y recién entonces la socia paga a los ingenieros.
4. **Lo que gana la socia.** Una comisión escalonada de **7 % / 6 % / 5 %** sobre la remuneración bruta de los ingenieros liquidada en el mes, con un **mínimo mensual**; los gastos reales (bancarios, etc.) se trasladan al costo. A los ingenieros no se les cobra nada. Ver ejemplo en §4.
5. **Piloto.** Nueve meses; los tres primeros son de ajuste. En el mes 9 ambas partes deciden si amplían la colaboración según recompra de clientes, margen y opinión legal. Si más adelante Qiao crea una WFOE en una zona piloto de Shenzhen, la socia tendrá prioridad para negociar servicios de transición.

---

## 2. La plataforma y el flujo

### 2.1 Qué hace hoy el MVP

- Revisión de proyectos: las categorías prohibidas no se pueden elegir; las palabras sensibles y el lenguaje de "empleo" se marcan para revisión manual; el cliente firma declaraciones (sin tecnología controlada, sin datos sensibles de personas de EE. UU. en volumen, sin empleo, sin categoría prohibida).
- Revisión de perfiles; el cliente sólo ve **perfiles seudonimizados** (alias, habilidades, años, provincia, idiomas, tarifa). El CV completo requiere **consentimiento del ingeniero para cada proyecto**.
- Cada parte acepta en línea sólo su propio contrato; prepago, entrega, aceptación, revisión y disputa de hitos quedan registrados (hora, IP, huella SHA-256 del texto).
- Exportación mensual de liquidaciones y **API para la socia**: la socia descarga el lote y devuelve referencia de pago, IIT retenido y neto pagado.
- La app de ingenieros funciona desde China continental **sin VPN** y no hace ninguna solicitud a servicios externos.
- Admite **equipos**: un equipo es sólo una forma de trabajar juntos, **no es persona jurídica**; cada integrante sigue siendo un contratista individual con su propio acuerdo y su propia línea de liquidación.

### 2.2 Flujo

```
Cliente extranjero ─(KYB y sanciones)─> publica proyecto ─> revisión de compliance ─> postulaciones ─> selección
                                                                                              │
          ┌───────────────────────────────────────────────────────────────────────────────────┘
          ▼
  Documentos: ① HK Ltd–cliente: Acuerdo de Servicios del Proyecto
              ② Socia–ingeniero: Acuerdo de Entregables
              ③ Orden de trabajo (SOW) bajo el contrato marco, generada por la plataforma y confirmada por la socia
          │
          ▼
  Prepago del hito a HK Ltd ─> entrega ─> aceptación del cliente
          │
          ▼ (inicio de cada mes)
  Lote de liquidación ─> factura de exportación de la socia ─> HK Ltd paga en USD
          │
          ▼
  La socia convierte, paga en RMB y retiene IIT ─> informa por API
          │
          ▼
  Registro del contrato de exportación de tecnología, reportes fiscales, etc.
```

### 2.3 Reparto de tareas

| Tarea | HK Ltd (Qiao) | Socia |
| --- | --- | --- |
| Captar clientes, contratar con ellos y cobrar | Sí | — |
| Revisión de proyectos y categorías; control de sanciones del cliente | Sí | Informada, con veto |
| KYC real del ingeniero y verificación de la cuenta | Guarda sólo la referencia de la socia | Sí |
| Contratar al ingeniero (por entregables) | Da modelo y datos | Sí |
| Pagar y retener IIT | Paga en plazo | Sí |
| Reporte de información fiscal (Decreto 810, si aplica) | Da los datos | Sí (como sujeto o agente local, a confirmar por abogados) |
| Registro del contrato de exportación de tecnología | Firma y documentos | Sí |
| Factura de exportación y declaración con tasa cero | — | Sí |
| Representante PIPL art. 53 (si aplica) | Responsable final; lo notifica a la autoridad | Sí, con carta de designación separada |
| Gestión del proyecto, aceptación, disputas | Sí | Acceso a los registros |

---

## 3. Rol y obligaciones de la socia

1. **Contratación.** Firma en nombre propio con cada ingeniero un Acuerdo de Entregables: pago por hito entregado, **sin horario fijo, sin lugar fijo y sin exclusividad**; el ingeniero puede trabajar para otros. El modelo se acuerda entre las partes.
2. **Pago.** Dentro de los [5] días hábiles de recibir el pago de HK Ltd, paga en RMB a la cuenta verificada del ingeniero. El tipo de cambio es el que efectivamente obtiene la socia (comprobante bancario) y se traslada tal cual: la socia no asume riesgo cambiario ni gana diferencia de cambio.
3. **Retención de IIT.** La socia, como pagadora, es agente de retención. Nuestra lectura: el Anuncio SAT 2025 N.º 16 permite a las **empresas de plataforma** aplicar el **método acumulativo** (el mismo de los sueldos) a la remuneración por servicios de los trabajadores de la plataforma, desde el 1/10/2025. **Si la socia puede usar ese método, o si debe aplicar la preretención general de remuneración por servicios (20 %–40 %), lo tienen que confirmar abogados y asesor tributario**; es una de las preguntas del memo legal.
4. **Reporte de información fiscal.** El Decreto del Consejo de Estado N.º 810 (vigente desde el 20/6/2025) obliga a las plataformas a informar trimestralmente identidad e ingresos de quienes trabajan a través de ellas; si una plataforma extranjera no tiene entidad operativa en China, informa un agente local designado. Proponemos que la socia asuma ese reporte (si aplica), en la forma que definan los abogados.
5. **Registro del contrato de exportación de tecnología.** Según las Medidas de registro de contratos de importación y exportación de tecnología, los contratos de tecnología de libre exportación (incluidos los de servicios técnicos) se registran dentro de los 60 días de su entrada en vigor en el sistema en línea del MOFCOM. Cómo se registran el contrato marco y cada SOW lo definen los abogados.
6. **KYC.** La socia verifica identidad y cuenta del ingeniero y genera la "referencia de socia". Documento, cuenta bancaria y rostro **quedan sólo en los sistemas de la socia en China**; la plataforma no los guarda.
7. **Representante PIPL.** Si los abogados confirman que HK Ltd necesita un representante en China según el art. 53 de la PIPL, proponemos que sea la socia, con funciones limitadas a recibir, reenviar y coordinar comunicaciones de la autoridad, bajo una carta de designación separada; HK Ltd mantiene la responsabilidad final e indemniza a la socia.
8. **Facturas y comprobantes.** La socia factura a HK Ltd la exportación del servicio y declara la tasa cero; además necesita comprobantes válidos para deducir lo que paga a los ingenieros (por ejemplo, factura emitida por la oficina tributaria a pedido del ingeniero, u otra vía admitida). Lo confirma el asesor tributario.
9. **Archivo y cooperación.** Conserva contratos, pagos, retenciones y registros durante los plazos legales y coopera con las verificaciones pactadas.

**Lo que la socia no tiene que hacer:** buscar clientes, gestionar el trabajo diario de los ingenieros, publicar avisos de empleo ni adelantar dinero.

---

## 4. Lo que gana la socia: estructura de comisión y ejemplo

### 4.1 Razonamiento

- La socia asume la posición de **contratante principal** (parte del contrato, agente de retención, titular del registro), no sólo la de "pagadora". La comisión tiene que cubrir personal, sistemas, costo de cumplimiento y riesgo, y dejar un margen razonable.
- Una tarifa fija sola se vuelve cara para Qiao cuando crece el volumen; un porcentaje solo no cubre el costo fijo de la socia mientras el piloto es chico. Por eso: **porcentaje escalonado + mínimo mensual**.
- Supuesto de costo incremental de la socia: alrededor de media persona-mes de finanzas/nómina y algo de compliance, unos RMB 6.000–10.000 por mes. **Es un supuesto nuestro; hay que pedirle a cada candidata su propio cálculo.** No encontramos una fuente pública confiable sobre comisiones de mercado de plataformas de pago a independientes en China, así que conviene pedir dos o tres cotizaciones para comparar.

### 4.2 Propuesta (para negociar)

| Concepto | Propuesta |
| --- | --- |
| Base | Remuneración bruta (USD, antes de IIT) de los ingenieros en el lote del mes |
| Tramos (marginales) | Hasta USD 50.000/mes: **7 %**; de USD 50.000 a 150.000: **6 %**; más de USD 150.000: **5 %** |
| Mínimo mensual | Meses 1–3: **RMB 6.000**; desde el mes 4: **RMB 8.000** (se paga el mayor entre mínimo y porcentaje; no se suman) |
| Gastos trasladables | Comisiones bancarias de ingreso, costo de conversión, impuesto de sellos del contrato, firma electrónica, verificación de identidad: al costo, sin recargo, detallados cada mes |
| Pago | En la misma factura que la liquidación (o separado); HK Ltd paga dentro de 5 días hábiles de recibida la factura |
| Ingenieros | No pagan nada |
| Exclusividad | Ninguna de las dos partes en el piloto; prioridad de negociación para la socia en la transición a la WFOE |

### 4.3 Ejemplo

Supuestos (sólo para el cálculo; no son cotización ni pronóstico): remuneración de **USD 3.000 por ingeniero por mes** (bruta); **1 USD = RMB 7,10**; comisión de Qiao al cliente 25 % (se cobra aparte, no afecta la comisión de la socia).

| Escenario | Ingenieros en el mes | Remuneración total (USD) | Comisión por porcentaje | Mínimo | Comisión del mes |
| --- | --- | --- | --- | --- | --- |
| A: mes 2 del piloto | 3 | 9.000 | USD 630 ≈ RMB 4.473 | RMB 6.000 | **RMB 6.000** |
| B: mes 6 del piloto | 8 | 24.000 | USD 1.680 ≈ RMB 11.928 | RMB 8.000 | **RMB 11.928** |
| C: con escala | 30 | 90.000 | 50.000×7 % + 40.000×6 % = USD 5.900 ≈ RMB 41.890 | RMB 8.000 | **RMB 41.890** (≈ RMB 503.000 al año) |

Notas:
- La socia factura a HK Ltd "remuneración + comisión + gastos". En el escenario C su ingreso mensual por exportación de servicios ronda USD 95.900; la comisión es su margen bruto y la remuneración su costo.
- Ese ingreso es de outsourcing offshore y podría ayudar a la socia a cumplir requisitos de beneficios como el de Empresa de Servicios de Tecnología Avanzada (Caishui [2017] N.º 79 exige, entre otros, que el outsourcing offshore sea al menos 35 % de los ingresos). **Depende de las reglas locales; no lo prometemos.**

### 4.4 Impacto en Qiao (para que la socia vea nuestro margen)

Con USD 3.000 por ingeniero-mes y comisión de 25 %, el cliente paga USD 3.750. Restando costo de cobro (supuesto ~3,5 %, tarjeta por Stripe) y 7 % de la socia, a Qiao le quedan unos **USD 409 por ingeniero-mes**. La comisión de la socia se lleva ~28 % de la comisión de Qiao; por eso los tramos bajan con el volumen.

---

## 5. Por qué es lícito y atractivo (nuestra lectura, a confirmar)

1. **El outsourcing de servicios está promovido.** El MOFCOM y otros cinco organismos publicaron en diciembre de 2025 el Plan de Acción para el desarrollo de alta calidad del outsourcing de servicios.
2. **Exportación de servicios con IVA tasa cero.** El Reglamento de la Ley del IVA (vigente desde el 1/1/2026), art. 9: tasa cero para servicios de I+D, software, sistemas de información y outsourcing offshore prestados por una entidad china a una entidad extranjera y consumidos íntegramente en el exterior. Los criterios y el trámite dependen de normas complementarias.
3. **Beneficio para empresas de servicios de tecnología avanzada:** impuesto a las ganancias del 15 % (Caishui [2017] N.º 79).
4. **Reglas fiscales más claras, más espacio para quien cumple.** El Anuncio SAT 2025 N.º 16 y el Decreto 810 ordenan la retención y el reporte. A la vez, el SAT persigue con dureza a las plataformas de "empleo flexible" que facturaban servicios ficticios (primera difusión conjunta de casos, diciembre de 2025). En este modelo cada pago corresponde a un proyecto real, con entregable y aceptación registrados.
5. **Lo que no hacemos.** No publicamos empleos, no hacemos intermediación laboral ni cesión de personal; la socia compra y vende servicios en nombre propio y no cobra ni paga por cuenta de terceros. Si eso alcanza para quedar fuera de la licencia de servicios de RR. HH. y de las licencias de telecomunicaciones es la pregunta central del memo legal.
6. **Datos mínimos.** La plataforma guarda perfiles seudonimizados y la referencia de la socia; los datos sensibles se tratan sólo en China, por la socia. Hay un contador anual de transferencias con umbrales de alerta, y el CV completo exige consentimiento por proyecto, en línea con el criterio de "necesidad y mínimo" que la CAC aplicó a los CV en su Q&A de julio de 2026 (nuestra lectura).

---

## 6. Riesgos y mitigación

| Riesgo | Descripción | Mitigación |
| --- | --- | --- |
| Relación laboral de hecho | Un ingeniero que trabaja mucho tiempo para el mismo cliente podría verse como empleado | Sólo proyectos con entregables y fin (≤ 12 meses); sin campos de sueldo, horario ni presencialidad; marcado automático de lenguaje de empleo; pago por hito; sin exclusividad; la socia no controla asistencia ni dirige el trabajo |
| Servicio de RR. HH. sin licencia | La intermediación podría verse como agencia de colocación o reclutamiento en línea | Sin avisos de empleo, sin cobro a ingenieros, sólo servicios técnicos; opinión legal específica |
| Facturación falsa y "liquidación secundaria" (二清) | El sector de empleo flexible está bajo inspección | La socia es compradora y vendedora real; cada liquidación lleva un "paquete de evidencia" (SOW, huella del entregable, aceptación, pago); coinciden contrato, factura, fondos y entregable; nada de cobrar por cuenta de terceros |
| Control de exportaciones | Ciertas tecnologías tienen exportación restringida o prohibida | Categorías prohibidas bloqueadas por sistema; control contra el Catálogo de tecnologías de exportación prohibida o restringida; veto de la socia |
| Sanciones o entidades no confiables del lado del cliente | El cliente podría ser una parte restringida | KYB y control de listas por HK Ltd (incluida la lista de entidades no confiables y la de contramedidas); la socia puede rechazar clientes o proyectos |
| Datos personales y transferencia | La plataforma trata perfiles en Hong Kong | Minimización; datos sensibles en China; consentimientos separados; contador con alertas; contrato estándar o certificación si corresponde; evaluación de impacto anual |
| HK Ltd no paga | La socia ya contrató al ingeniero | Prepago del cliente; la socia paga sólo después de cobrar; depósito de garantía opcional (p. ej. 50 % de un mes de liquidación estimada) |
| Tipo de cambio | Se fija en USD y se paga en RMB | Se traslada el tipo de cambio real |
| Cambio normativo | Las reglas siguen evolucionando | Cláusula de cambio de ley: 30 días para adaptar; si no, salida sin penalidad |

---

## 7. Capacidades que debe tener la socia

**Imprescindibles:**
- Empresa constituida en China con objeto social que incluya desarrollo de software, servicios de TI y servicios/desarrollo/transferencia de tecnología.
- Calificación de crédito tributario A o B; sin antecedentes de facturación falsa, infracciones tributarias graves ni listas de incumplidores.
- Capacidad de recibir USD del exterior y convertir; emitir facturas de exportación de servicios y declarar tasa cero (o exención).
- Sistema y experiencia de retención de IIT para decenas o cientos de personas por mes.
- Experiencia en registro de contratos de exportación de tecnología, o disposición a hacerlo con guía de abogados.
- Resguardo seguro de datos de identidad en China (políticas básicas de seguridad; idealmente registro MLPS / 等级保护).
- Sede o operación principal en Shenzhen, Guangzhou o Shanghái.

**Suman:**
- Negocio de outsourcing offshore, o certificación de empresa de servicios de tecnología avanzada o de alta tecnología.
- Experiencia con clientes de Europa, EE. UU., Japón o Asia-Pacífico, con referencias (pueden ser anonimizadas).
- Procesos para conflictos o temas fiscales con contratistas individuales.

---

## 8. Próximos pasos

1. **Semana 1:** llamada de 30–45 minutos con demo de liquidación y API; NDA.
2. **Semana 2:** la socia envía documentos de due diligence (anexo 1); Qiao envía los suyos (anexo 2).
3. **Semanas 3–4:** carta de intención no vinculante con comisión y puntos clave del contrato marco.
4. **Semanas 4–8:** abogados de ambas partes revisan el contrato marco (chino e inglés); Qiao obtiene su memo legal.
5. **Después de firmar:** uno o dos proyectos de prueba para validar contratación, pago, retención y registro.
6. **Mes 9:** evaluación conjunta: ampliar, ajustar o cerrar ordenadamente.

---

## Anexo 1: documentos que pedimos a la socia

Licencia comercial y estatutos; estados financieros auditados de los dos últimos años; constancia de calificación de crédito tributario; resumen de retenciones de IIT de los últimos 12 meses (anonimizado); certificados de registro de contratos de exportación de tecnología (si tiene, anonimizados); cuenta para cobros en moneda extranjera; certificaciones (empresa de servicios de tecnología avanzada, alta tecnología, ISO 27001, MLPS) si las tiene; detalle de litigios, arbitrajes y sanciones administrativas; organigrama del equipo que atendería la cuenta (sin datos personales).

## Anexo 2: lo que aporta Qiao

Descripción funcional y entorno de demo; lista de campos de datos y diagrama de flujo de datos; los tres modelos de contrato (cliente, entregables del ingeniero, borrador de contrato marco); lista de categorías prohibidas y flujo de revisión; especificación de la API de liquidación; lista de preguntas del memo legal.

## Anexo 3: preguntas frecuentes

- **¿La socia gestiona a los ingenieros?** No. Gestión, aceptación y disputas las lleva la plataforma; la socia puede consultar los registros.
- **¿El ingeniero ve el nombre del cliente y el precio?** No. Ve sólo el país del cliente y su propia remuneración. La socia sí ve el nombre del cliente, para sus controles.
- **¿Y si el cliente no queda conforme?** Puede pedir revisión o abrir disputa; sólo entran en la liquidación los hitos aceptados (o resueltos a favor del pago). La socia nunca paga trabajo no aceptado.
- **¿Hay que usar el sistema de Qiao?** Sólo para recibir los datos (API o CSV); la socia sigue usando sus propios sistemas contables y de IIT.

---

### Fuentes (verificadas sólo a través de resúmenes de búsqueda: WebFetch está bloqueado en este entorno, así que no se pudo abrir el texto completo; revisar los originales antes de usar)

- Anuncio SAT 2025 N.º 16: [base normativa del SAT](https://fgk.chinatax.gov.cn/zcfgk/c100012/c5241472/content.html); [reproducción de la oficina de Jiangsu](https://jiangsu.chinatax.gov.cn/art/2025/6/30/art_8349_509969.html)
- Decreto 810: [base normativa del SAT](https://fgk.chinatax.gov.cn/zcfgk/c100010/c5241238/content.html); [análisis de Han Kun](https://hankunlaw.com/portal/article/index/cid/8/id/15440.html)
- Reglamento de la Ley del IVA: [Boletín del Consejo de Estado](https://www.gov.cn/gongbao/2026/issue_12486/202601/content_7054232.html); [Xinhua](https://www.news.cn/20251230/e0e9179e77bb4fb290b042d03b39f970/c.html)
- Medidas de registro de contratos de tecnología (MOFCOM 2009 N.º 3): [WIPO Lex](https://www.wipo.int/wipolex/zh/text/199778)
- Plan de Acción de outsourcing de servicios (dic. 2025): [gov.cn](https://www.gov.cn/lianbo/bumen/202512/content_7051781.htm); [Xinhua](https://www.news.cn/fortune/20251215/ee85779f19bd4c5b9e068615a2337e77/c.html)
- Caishui [2017] N.º 79: [base normativa del SAT](https://fgk.chinatax.gov.cn/zcfgk/c102416/c5202506/content.html)
- Casos de plataformas difundidos por el SAT (dic. 2025): [Xinhua](https://www.news.cn/legal/20251217/60a8639280fa40609469ff9641d6147c/c.html)
- Q&A de la CAC de julio de 2026: [CAC](https://www.cac.gov.cn/2026-07/24/c_1786638883119336.htm)
- Catálogo de tecnologías de exportación prohibida o restringida (2023): [Beijing News](https://www.bjnews.com.cn/detail/1703153105129008.html)
- Costo de cobro con tarjeta (~3,4–3,9 %, fuente de terceros, sólo estimación): [FAQ de Stripe para Singapur y Hong Kong](https://support.stripe.com/questions/faq-for-pricing-of-international-card-payments-in-singapore-and-hong-kong)
