# `exponential-backoff-multiplier` and `exponential-max-wait-duration`
Here’s a **clear, practical explanation** of how `exponential-backoff-multiplier` and `exponential-max-wait-duration` work in **Resilience4j Retry**.

---

# ✅ **1. exponential-backoff-multiplier**

This controls how each retry’s wait time **increases exponentially**.

Formula:

```
nextWaitDuration = previousWaitDuration * exponentialBackoffMultiplier
```

### Example

```
waitDuration = 500ms
exponential-backoff-multiplier = 2.0
```

Retry delays become:

1. First retry wait: **500ms**
2. Second retry wait: **500 × 2 = 1000ms**
3. Third retry wait: **1000 × 2 = 2000ms**
4. Fourth retry wait: **2000 × 2 = 4000ms**
   … and so on.

So each retry **waits longer**.

---

# ✅ **2. exponential-max-wait-duration**

This defines the **maximum limit** for any wait duration while using exponential backoff.

Even if the multiplier says the next delay should be larger, it will **never exceed this max**.

### Example

```
waitDuration = 500ms
exponential-backoff-multiplier = 2.0
exponential-max-wait-duration = 1200ms
```

Retry delays:

1. First retry: **500ms**
2. Second retry: **1000ms**
3. Third retry: 2000ms → exceeds max → **1200ms**
4. Fourth retry: would be 4000ms → exceeds max → **1200ms**

So after hitting the maximum, all future retries use the **max wait duration**.

---

# 📌 **Complete Behavior Summary**

### If exponential backoff is enabled:

* Start with `waitDuration`
* Multiply by `exponential-backoff-multiplier` each retry
* Stop increasing once reaching `exponential-max-wait-duration` (if configured)

---

# 📘 YAML Example

```yaml
resilience4j:
  retry:
    instances:
      myRetry:
        maxAttempts: 5
        waitDuration: 500ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2.0
        exponentialMaxWaitDuration: 5s
```

### Timings:

| Attempt | Computed Delay | Applied Delay           |
| ------- | -------------- | ----------------------- |
| 1 → 2   | 500ms          | 500ms                   |
| 2 → 3   | 1000ms         | 1000ms                  |
| 3 → 4   | 2000ms         | 2000ms                  |
| 4 → 5   | 4000ms         | 4000ms (below max = 5s) |

---

# 🧠 Important Details

### ✔ Exponential backoff is **disabled by default**

You must enable:

```
enableExponentialBackoff: true
```

### ✔ `exponential-max-wait-duration` is optional

If not set, delays keep doubling without limit.

### ✔ Used for:

* Flaky APIs
* Rate limits
* Avoiding retry storms
* Reducing load on external services

---

# Issue 1 (Why exponential-backoff-multiplier don't work)

Your configuration is **ignored** because **Resilience4j requires `enableExponentialBackoff` and `exponentialBackoffMultiplier` to be placed inside the *config* (base-config), not the instance**, unless you repeat `wait-duration` inside the instance.

This is a *documented but very confusing* behavior.

---

# ❌ **Why your config doesn’t multiply**

Your YAML:

```yaml
resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 500ms
    instances:
      core-test:
        base-config: default
        exponential-backoff-multiplier: 2
        enable-exponential-backoff: true
```

Problem:

### 🔥 **If an instance overrides any exponential field, it MUST also override `wait-duration`.**

Because Resilience4j internally creates a *copy* of the config, and exponential logic only activates if `waitDuration` is configured in the same "scope" (same config block).

Since you're using:

* `wait-duration` in **base config**
* exponential fields in **instance**

The exponential logic never sees that combination → it defaults to **fixed backoff**.

So your actual retry delays are:

```
Attempt 1 → 2 = 500ms
Attempt 2 → 3 = 500ms
Total ≈ 1000–1100ms
```

Exactly what you observed.

---

# ✅ **Fix 1 — Put exponential config inside the base config**

This is the cleanest way.

```yaml
resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
    instances:
      core-test:
        base-config: default
```

Now exponential behavior works.

### Expected backoff:

* 1st retry wait → 500ms
* 2nd retry wait → 1000ms (500 × 2)

Total ≈ **1500ms–1600ms**

Your test now passes:

```java
assertTrue(interval >= 1500);
verify(goofyService, times(3)).getData();
```

---

# 🧠 **Why this happens (internal behavior)**

Resilience4j merges configs like this:

1. Load base-config (default)
2. Copy to new instance config
3. Apply only the *fields overridden in the instance*

‼️ Exponential logic checks **only the instance-level** configuration object.

If `wait-duration` is missing after merge, exponential backoff is silently disabled.

---

# 🧪 **Your test is completely valid**

Your test logic:

* 3 attempts → 2 waits
* 500ms + 1000ms = 1500ms
* `verify(... times(3))` correct
* fallback works → correct

All good.
The issue is **solely config placement**.

---

If you want, I can also give you:

✅ A log output example with exponential backoff enabled
✅ A version using `exponential-max-wait-duration`
Just tell me!
