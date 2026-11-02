// tailwind.config.js
module.exports = {
  content: ["./src/**/*.{html,ts}", "./src/**/*.css"],
  theme: {
    extend: {
      colors: {
        primary: "oklch(51.01% 0.274 263.83)",
        accent: "oklch(53.18% 0.28 296.97)"
      }
    }
  },
  plugins: []
};
