<template>
  <svg viewBox="0 0 600 520" fill="none" xmlns="http://www.w3.org/2000/svg" class="decor-svg">
    <!-- 背景圆点网格 -->
    <defs>
      <pattern id="dotGrid" x="0" y="0" width="28" height="28" patternUnits="userSpaceOnUse">
        <circle cx="1.5" cy="1.5" r="1.2" fill="currentColor" opacity="0.07" />
      </pattern>
      <linearGradient id="gradPink" x1="0" y1="0" x2="1" y2="1">
        <stop offset="0%" stop-color="#fe2c55" stop-opacity="0.18" />
        <stop offset="100%" stop-color="#ff6b81" stop-opacity="0.06" />
      </linearGradient>
      <linearGradient id="gradPurple" x1="1" y1="0" x2="0" y2="1">
        <stop offset="0%" stop-color="#667eea" stop-opacity="0.2" />
        <stop offset="100%" stop-color="#764ba2" stop-opacity="0.08" />
      </linearGradient>
      <linearGradient id="gradDark" x1="0.5" y1="0" x2="0.5" y2="1">
        <stop offset="0%" stop-color="#764ba2" stop-opacity="0.22" />
        <stop offset="100%" stop-color="#667eea" stop-opacity="0.1" />
      </linearGradient>
    </defs>

    <!-- 圆点底纹 -->
    <rect width="600" height="520" fill="url(#dotGrid)" />

    <!-- 大号装饰圆形 -->
    <circle cx="140" cy="160" r="130" fill="url(#gradPink)" />
    <circle cx="300" cy="210" r="105" fill="url(#gradPurple)" />
    <circle cx="430" cy="130" r="70" fill="url(#gradDark)" />

    <!-- 小号点缀圆 -->
    <circle cx="80" cy="350" r="16" fill="#fe2c55" opacity="0.15" />
    <circle cx="200" cy="420" r="22" fill="#667eea" opacity="0.18" />
    <circle cx="480" cy="380" r="12" fill="#764ba2" opacity="0.2" />

    <!-- 数据柱状条 -->
    <rect x="95" y="280" width="22" height="90" rx="6" fill="#fe2c55" opacity="0.22" />
    <rect x="130" y="250" width="22" height="120" rx="6" fill="#fe2c55" opacity="0.14" />
    <rect x="165" y="300" width="22" height="70" rx="6" fill="#667eea" opacity="0.18" />
    <rect x="200" y="235" width="22" height="135" rx="6" fill="#667eea" opacity="0.1" />

    <!-- 圆环图 -->
    <circle cx="410" cy="290" r="42" stroke="#fe2c55" stroke-width="10" opacity="0.18" />
    <circle cx="410" cy="290" r="42" stroke="#764ba2" stroke-width="8" opacity="0.15"
            stroke-dasharray="160 100" stroke-linecap="round" transform="rotate(-90 410 290)" />

    <!-- 折线 -->
    <polyline
      points="300,380 345,355 390,370 435,340 480,360"
      stroke="#667eea" stroke-width="3" opacity="0.2" fill="none"
      stroke-linecap="round" stroke-linejoin="round"
    />
    <circle cx="300" cy="380" r="5" fill="#667eea" opacity="0.25" />
    <circle cx="345" cy="355" r="5" fill="#667eea" opacity="0.25" />
    <circle cx="390" cy="370" r="5" fill="#667eea" opacity="0.25" />
    <circle cx="435" cy="340" r="5" fill="#667eea" opacity="0.25" />
    <circle cx="480" cy="360" r="5" fill="#667eea" opacity="0.25" />

    <!-- 底部装饰横条 -->
    <rect x="60" y="460" width="120" height="4" rx="2" fill="#fe2c55" opacity="0.15" />
    <rect x="200" y="460" width="80" height="4" rx="2" fill="#667eea" opacity="0.12" />
    <rect x="300" y="460" width="160" height="4" rx="2" fill="#764ba2" opacity="0.1" />
  </svg>
</template>

<style scoped>
.decor-svg {
  width: 100%;
  height: auto;
  display: block;
}
</style>
