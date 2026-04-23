package dev.chams.render;

import dev.chams.ChamsMod;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Extrahiert die Skin-/Modell-Textur aus einem beliebigen RenderType.
 * Kann außerdem einen RenderType klonen und dabei nur den Tiefentest
 * deaktivieren – alle anderen Pipeline-Eigenschaften (Shader, Cull,
 * Blend, VertexFormat) werden 1:1 übernommen.
 */
public final class ChamsRenderTypeTransform {

    private ChamsRenderTypeTransform() {}

    // ------------------------------------------------------------------
    //  Reflection-Felder
    // ------------------------------------------------------------------
    private static Field stateField;        // RenderType.state          (RenderSetup)
    private static Field texturesField;     // RenderSetup.textures      (Map<String,TextureBinding>)
    private static Field locationField;     // TextureBinding.location   (Identifier)
    private static Field pipelineField;     // RenderSetup.pipeline      (RenderPipeline)
    private static Field useLightmapField;  // RenderSetup.useLightmap   (boolean)
    private static Field useOverlayField;   // RenderSetup.useOverlay    (boolean)
    private static boolean initFailed = false;

    static {
        try {
            stateField = RenderType.class.getDeclaredField("state");
            stateField.setAccessible(true);

            Class<?> renderSetupClass =
                    Class.forName("net.minecraft.client.renderer.rendertype.RenderSetup");

            texturesField = renderSetupClass.getDeclaredField("textures");
            texturesField.setAccessible(true);

            pipelineField = renderSetupClass.getDeclaredField("pipeline");
            pipelineField.setAccessible(true);

            useLightmapField = renderSetupClass.getDeclaredField("useLightmap");
            useLightmapField.setAccessible(true);

            useOverlayField = renderSetupClass.getDeclaredField("useOverlay");
            useOverlayField.setAccessible(true);

            Class<?> textureBindingClass =
                    Class.forName("net.minecraft.client.renderer.rendertype.RenderSetup$TextureBinding");
            locationField = textureBindingClass.getDeclaredField("location");
            locationField.setAccessible(true);

        } catch (ReflectiveOperationException e) {
            initFailed = true;
            ChamsMod.LOGGER.error("ChamsRenderTypeTransform init failed", e);
        }
    }

    // ------------------------------------------------------------------
    //  Caches
    // ------------------------------------------------------------------
    private static final Map<RenderType, Identifier>  TEXTURE_CACHE  = new ConcurrentHashMap<>();
    private static final Map<RenderType, RenderType>  NO_DEPTH_CACHE = new ConcurrentHashMap<>();
    private static final AtomicInteger CHAMS_ID = new AtomicInteger(0);

    // ------------------------------------------------------------------
    //  Texture extraction
    // ------------------------------------------------------------------

    /** Versucht, die "Sampler0"-Textur aus dem RenderType zu ziehen. */
    public static Optional<Identifier> extractTexture(RenderType rt) {
        if (initFailed || rt == null) return Optional.empty();

        Identifier cached = TEXTURE_CACHE.get(rt);
        if (cached != null) return Optional.of(cached);

        try {
            Object setup = stateField.get(rt);
            if (setup == null) return Optional.empty();

            @SuppressWarnings("unchecked")
            Map<String, ?> textures = (Map<String, ?>) texturesField.get(setup);
            if (textures == null || textures.isEmpty()) return Optional.empty();

            Object binding = textures.get("Sampler0");
            if (binding == null) return Optional.empty();

            Identifier loc = (Identifier) locationField.get(binding);
            if (loc != null) TEXTURE_CACHE.put(rt, loc);
            return Optional.ofNullable(loc);

        } catch (ReflectiveOperationException e) {
            return Optional.empty();
        }
    }

    /**
     * Heuristik: Glint-/Foil-Overlay für verzauberte Items/Rüstung.
     * Diese Passes liefern kein brauchbares Bild über unsere Pipeline.
     */
    public static boolean isGlintTexture(Identifier id) {
        if (id == null) return false;
        String path = id.getPath();
        return path.contains("glint") || path.contains("enchanted_item");
    }

    // ------------------------------------------------------------------
    //  Pipeline-Klon: nur Tiefentest + Depth-Write deaktivieren,
    //  alle anderen Eigenschaften (Shader, Cull, Blend, VertexFormat,
    //  Textur-Bindings, Lightmap, Overlay) identisch übernehmen.
    // ------------------------------------------------------------------

    /**
     * Klont {@code original} und ersetzt nur den Tiefentest durch
     * {@link DepthTestFunction#NO_DEPTH_TEST} und schaltet Depth-Write ab.
     * Shader, VertexFormat, Cull, Blend etc. bleiben unverändert.
     *
     * @return den geklonten RenderType, oder {@code null} bei Fehler
     *         (Aufrufer soll dann auf den generischen Fallback ausweichen)
     */
    public static RenderType cloneWithNoDepth(RenderType original) {
        if (initFailed || original == null) return null;

        RenderType cached = NO_DEPTH_CACHE.get(original);
        if (cached != null) return cached;

        try {
            Object setup = stateField.get(original);
            if (setup == null) return null;

            RenderPipeline orig     = (RenderPipeline) pipelineField.get(setup);
            @SuppressWarnings("unchecked")
            Map<String, ?> textures = (Map<String, ?>) texturesField.get(setup);
            boolean useLightmap     = (boolean) useLightmapField.get(setup);
            boolean useOverlay      = (boolean) useOverlayField.get(setup);

            // Snippet mit den Original-Pipeline-Eigenschaften bauen.
            // Nur Tiefentest und Depth-Write werden danach überschrieben.
            RenderPipeline.Snippet snippet = new RenderPipeline.Snippet(
                    Optional.of(orig.getVertexShader()),
                    Optional.of(orig.getFragmentShader()),
                    Optional.of(orig.getShaderDefines()),
                    Optional.of(orig.getSamplers()),
                    Optional.of(orig.getUniforms()),
                    orig.getBlendFunction(),          // Optional<BlendFunction>
                    Optional.of(orig.getDepthTestFunction()),
                    Optional.of(orig.getPolygonMode()),
                    Optional.of(orig.isCull()),
                    Optional.of(orig.isWriteColor()),
                    Optional.of(orig.isWriteAlpha()),
                    Optional.of(orig.isWriteDepth()),
                    Optional.of(orig.getColorLogic()),
                    Optional.of(orig.getVertexFormat()),
                    Optional.of(orig.getVertexFormatMode())
            );

            // withLocation erwartet nur den Pfad-Teil; Minecraft ergänzt "minecraft:"
            // automatisch. Daher kein ":" im String – nur [a-z0-9/_.-] erlaubt.
            int id = CHAMS_ID.incrementAndGet();
            String loc = "chams_nd_" + id;

            // cull=true erzwingen: Rüstung benutzt normalerweise "armor_cutout_no_cull"
            // (cull=false) - damit rendert Vanilla beide Seiten und verlässt sich auf
            // den Tiefentest um die Rückseite zu verdecken. Ohne Tiefentest überdeckt
            // die später gezeichnete Rückseite die Vorderseite → wir sehen durch die
            // Rüstung/den Skin auf die innere Rückwand. Mit cull=true wird die
            // Rückseite hard gekillt, egal was die Original-Pipeline wollte.
            RenderPipeline chamsP = RenderPipeline.builder(snippet)
                    .withLocation(loc)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .withDepthWrite(false)
                    .withCull(true)
                    .build();

            // RenderSetup mit denselben Textur-Bindings, Lightmap und Overlay
            RenderSetup.RenderSetupBuilder rsb = RenderSetup.builder(chamsP);
            if (textures != null) {
                for (Map.Entry<String, ?> entry : textures.entrySet()) {
                    Identifier texLoc = (Identifier) locationField.get(entry.getValue());
                    if (texLoc != null) rsb.withTexture(entry.getKey(), texLoc);
                }
            }
            if (useLightmap) rsb.useLightmap();
            if (useOverlay)  rsb.useOverlay();

            RenderType chamsRt = RenderType.create("chams_nd/" + id, rsb.createRenderSetup());
            NO_DEPTH_CACHE.put(original, chamsRt);
            return chamsRt;

        } catch (Exception e) {
            ChamsMod.LOGGER.warn("cloneWithNoDepth failed for {}: {}", original, e.getMessage());
            return null;
        }
    }
}
