Here’s the **clearest and most correct explanation** of how
`randomized-wait-factor` works in Resilience4j Retry (with examples).

This setting is confusing for many people — but once you understand it, it’s simple.

---

# ✅ What `randomized-wait-factor` Does

It adds **random jitter** to each retry’s wait duration so the delay is *not fixed*.
This prevents retry storms and is commonly used together with:

* exponential backoff
* fixed backoff

---

# 🎯 **Formula**

If your base wait duration for a retry is:

```
baseWait = computed wait duration (fixed or exponential)
```

Then:

```
randomWait = baseWait ± (baseWait * randomizedWaitFactor)
```

So the final wait time is a random value in this range:

```
[ baseWait * (1 - factor) , baseWait * (1 + factor) ]
```

---

# 📌 **Example 1: fixed wait**

```
wait-duration = 1000ms
randomized-wait-factor = 0.5
```

Range:

```
min = 1000 * (1 - 0.5) = 500ms
max = 1000 * (1 + 0.5) = 1500ms
```

So each retry waits somewhere between:

> **500ms → 1500ms** (random)

---

# 📌 **Example 2: exponential backoff**

```
wait-duration = 500ms
exponential-backoff-multiplier = 2
randomized-wait-factor = 0.2
```

Retry wait calculations:

### Attempt 1 → 2:

```
base = 500ms
range = 500 ± 20%
= 400ms → 600ms
```

### Attempt 2 → 3:

```
base = 1000ms
range = 1000 ± 20%
= 800ms → 1200ms
```

So real retry pauses might look like:

* 1st retry: 417ms
* 2nd retry: 1134ms
  (Your actual values will vary)

---

# 📌 **Example 3: Together with exponential-max-wait-duration**

```
baseWait = 2000ms
maxWait = 2500ms
factor = 0.5
```

Compute range:

```
min = 2000 * 0.5 = 1000ms
max = 2000 * 1.5 = 3000ms
```

But max cannot exceed exponentialMaxWaitDuration:

```
final range = 1000ms → min(3000ms, 2500ms)
                = 1000ms → 2500ms
```

---

# ⚠️ Important Notes

### 1. **randomized-wait-factor applies *after* exponential calculation**

So the jitter is applied on top of the exponential wait.

### 2. **It applies on every retry individually**

Every retry has a different random duration.

### 3. **Value must be between 0 and 1**

Otherwise Resilience4j logs warnings or ignores the setting.

### 4. **If you want FULL jitter (0 → base × 2), use factor = 1.0**

Very useful for heavy load systems.

---

# 💡 YAML Example

```yaml
resilience4j:
  retry:
    instances:
      core-test:
        max-attempts: 4
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        exponential-max-wait-duration: 5s
        randomized-wait-factor: 0.3
```

Retry delays might be something like:

* Attempt 1 → 2: 520ms
* Attempt 2 → 3: 1280ms
* Attempt 3 → 4: 2960ms
  (jitter based on ±30%)

---

# 📌 Want a real test example?

If you want, I can show:

* `@Test` verifying jitter range
* how to log actual wait times
* how to combine jitter + exponential backoff + max wait duration

Just tell me — happy to help!
