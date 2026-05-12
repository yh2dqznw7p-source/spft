package ru.spft.gui.anim;

/**
 * Портировано из rockstar (moscow.rockstar.utility.animation.base.Animation).
 * Из изменений: удалены lombok @Generated-геттеры и метод nonono(), не используемые здесь.
 * Основная логика update() — 1-в-1.
 */
public class Animation {
   private long duration;
   private float value;
   private Easing easing;
   private long startTime;
   private float startValue;
   private float targetValue;
   private boolean done;

   public Animation(long duration, float initialValue, Easing easing) {
      this.duration = duration;
      this.easing = easing;
      this.value = initialValue;
      this.startValue = initialValue;
      this.targetValue = initialValue;
      this.done = true;
   }

   public Animation(long duration, Easing easing) {
      this(duration, 0.0F, easing);
   }

   public void update(boolean bool) {
      this.update(bool ? 1.0F : 0.0F);
   }

   public float update(float newValue) {
      long currentTime = System.currentTimeMillis();
      if (newValue != this.targetValue) {
         this.startValue = this.value;
         this.targetValue = newValue;
         this.startTime = currentTime;
         this.done = false;
      }

      long elapsed = currentTime - this.startTime;
      if (elapsed >= this.duration) {
         this.value = this.targetValue;
         this.done = true;
         return this.value;
      } else {
         float progress = (float) elapsed / (float) this.duration;
         float easedProgress = this.easing.ease(progress, 0.0F, 1.0F, 1.0F);
         this.value = this.startValue + (this.targetValue - this.startValue) * easedProgress;
         return this.value;
      }
   }

   public void setValue(float newValue) {
      this.value = newValue;
      this.startValue = newValue;
      this.targetValue = newValue;
      this.done = true;
   }

   public void reset(float initialValue) {
      this.value = initialValue;
      this.startValue = initialValue;
      this.targetValue = initialValue;
      this.done = true;
   }

   public void reset() {
      this.reset(0.0F);
   }

   public long getDuration() { return duration; }
   public float getValue() { return value; }
   public Easing getEasing() { return easing; }
   public float getTargetValue() { return targetValue; }
   public boolean isDone() { return done; }

   public void setDuration(long duration) { this.duration = duration; }
   public void setEasing(Easing easing) { this.easing = easing; }
}
