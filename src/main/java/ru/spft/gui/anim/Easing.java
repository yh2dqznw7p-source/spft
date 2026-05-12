package ru.spft.gui.anim;

/**
 * Портировано из rockstar (moscow.rockstar.utility.animation.base.Easing).
 * Из изменений: удалены lombok-аннотации, MathUtility.sin/cos заменены на Math.sin/cos.
 * Остальная логика 1-в-1.
 */
public interface Easing {
   Easing BAKEK = generate(0.45F, 1.45F, 0.49F, 1.15F);
   Easing BAKEK_SMALLER = generate(0.45F, 1.45F, 0.43F, 0.91F);
   Easing BAKEK_PAGES = generate(0.1F, 1.07F, 0.34F, 1.04F);
   Easing BAKEK_SIZE = generate(0.27F, 1.09F, 0.49F, 1.06F);
   Easing BAKEK_BACK = generate(0.62, -0.16, 0.8, 0.37);
   Easing BAKEK_MANY = generate(0.25, 1.07, 0.11, 1.1);
   Easing FIGMA_EASE_IN_OUT = generate(0.42, 0.0, 0.58, 1.0);

   Easing LINEAR = (t, b, c, d) -> c * t / d + b;

   Easing CUBIC_IN = (t, b, c, d) -> {
      float v;
      return c * (v = t / d) * v * v + b;
   };
   Easing CUBIC_OUT = (t, b, c, d) -> {
      float v;
      return c * ((v = t / d - 1.0F) * v * v + 1.0F) + b;
   };
   Easing CUBIC_IN_OUT = (t, b, c, d) -> {
      float a, s;
      return (a = t / (d / 2.0F)) < 1.0F
         ? c / 2.0F * a * a * a + b
         : c / 2.0F * ((s = a - 2.0F) * s * s + 2.0F) + b;
   };
   Easing SINE_OUT = (t, b, c, d) -> c * (float) Math.sin(t / d * (Math.PI / 2.0)) + b;

   static Easing generate(double x1, double y1, double x2, double y2) {
      return new Easing() {
         @Override
         public float ease(float t, float b, float c, float d) {
            if (d <= 0.0F || t <= 0.0F) return b;
            if (t >= d) return b + c;
            float progress = t / d;
            float tBez = solveTBez((float) x1, (float) x2, progress);
            float y = bezierY(tBez, (float) y1, (float) y2);
            return b + c * y;
         }

         private float solveTBez(float x1, float x2, float progress) {
            float t = progress;
            for (int i = 0; i < 8; i++) {
               float x = bezierX(t, x1, x2);
               float dx = bezierDX(t, x1, x2);
               if (Math.abs(x - progress) < 1.0E-5F || Math.abs(dx) < 1.0E-6F) break;
               t -= (x - progress) / dx;
               t = Math.max(0.0F, Math.min(1.0F, t));
            }
            return t;
         }

         private float bezierX(float t, float x1, float x2) {
            return 3.0F * (1.0F - t) * (1.0F - t) * t * x1
                 + 3.0F * (1.0F - t) * t * t * x2
                 + t * t * t;
         }

         private float bezierDX(float t, float x1, float x2) {
            return 3.0F * ((1.0F - t) * (1.0F - 3.0F * t) * x1
                 + (2.0F * t - 3.0F * t * t) * x2)
                 + 3.0F * t * t;
         }

         private float bezierY(float t, float y1, float y2) {
            return 3.0F * (1.0F - t) * (1.0F - t) * t * y1
                 + 3.0F * (1.0F - t) * t * t * y2
                 + t * t * t;
         }
      };
   }

   float ease(float t, float b, float c, float d);
}
