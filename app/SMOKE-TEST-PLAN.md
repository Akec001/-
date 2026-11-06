# Kaka Android smoke-test plan

After a successful ARM64 debug build, verify on a real Android phone:

1. Install alongside the original Xiaohe app and confirm both icons remain present.
2. Launch Kaka and confirm VPN permission can be granted.
3. Connect on mobile data, then test browsing and DNS resolution.
4. Keep traffic active for at least 10 minutes and record upload/download counters.
5. Switch between 4G/5G and Wi-Fi; confirm the VPN session recovers without repeated manual reconnects.
6. Test ChatGPT app and web access separately; record DNS, TLS, and connection failures distinctly.
7. Export app logs after any failure.

A successful CI build is not a substitute for these real-device network tests.
