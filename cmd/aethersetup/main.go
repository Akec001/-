//go:build windows

// Command aethersetup is a self-contained Windows installer for Kaka.
// It deliberately uses the Win32 Unicode MessageBox API instead of Walk
// controls: this avoids ANSI/code-page conversion problems and GUI creation
// failures on Windows installations with non-Chinese system locales.
package main

import (
	"archive/zip"
	"bytes"
	"embed"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"unsafe"

	"golang.org/x/sys/windows"
)

//go:embed payload/bundle.zip
var payload embed.FS

const (
	appName = "Kaka"
	mbOK = 0
	mbYesNo = 4
	mbIconInfo = 0x40
	mbIconError = 0x10
	idYes = 6
)

var (
	user32 = windows.NewLazySystemDLL("user32.dll")
	messageBoxW = user32.NewProc("MessageBoxW")
)

func main() {
	if messageBox("是否将卡卡客户端安装到本机？\n\n安装位置：\n"+installDir(), "卡卡安装程序", mbYesNo|mbIconInfo) != idYes {
		return
	}
	logf("installer started")
	if err := doInstall(installDir()); err != nil {
		logf("installation failed: %v", err)
		messageBox("安装失败：\n"+err.Error()+"\n\n详细记录：\n"+logPath(), "卡卡安装程序", mbOK|mbIconError)
		return
	}
	logf("installation completed")
	if messageBox("卡卡已安装成功。\n\n是否立即启动？", "卡卡安装程序", mbYesNo|mbIconInfo) == idYes {
		exe := filepath.Join(installDir(), appName+".exe")
		cmd := exec.Command(exe)
		cmd.Dir = installDir()
		if err := cmd.Start(); err != nil {
			logf("launch failed: %v", err)
			messageBox("客户端启动失败：\n"+err.Error()+"\n\n详细记录：\n"+logPath(), "卡卡", mbOK|mbIconError)
		}
	}
}

func messageBox(message, title string, flags uintptr) int {
	m, err1 := windows.UTF16PtrFromString(message)
	t, err2 := windows.UTF16PtrFromString(title)
	if err1 != nil || err2 != nil {
		return 0
	}
	r, _, _ := messageBoxW.Call(0, uintptr(unsafe.Pointer(m)), uintptr(unsafe.Pointer(t)), flags)
	return int(r)
}

func installDir() string {
	base := os.Getenv("LOCALAPPDATA")
	if base == "" {
		base = filepath.Join(os.Getenv("USERPROFILE"), "AppData", "Local")
	}
	return filepath.Join(base, appName)
}

func logPath() string {
	base := os.Getenv("LOCALAPPDATA")
	if base == "" {
		base = os.TempDir()
	}
	return filepath.Join(base, "Kaka-Setup.log")
}

func logf(format string, args ...any) {
	line := fmt.Sprintf(format, args...) + "\r\n"
	f, err := os.OpenFile(logPath(), os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err == nil {
		_, _ = f.WriteString(line)
		_ = f.Close()
	}
}

func doInstall(dir string) error {
	raw, err := payload.ReadFile("payload/bundle.zip")
	if err != nil {
		return fmt.Errorf("读取内置安装包：%w", err)
	}
	zr, err := zip.NewReader(bytes.NewReader(raw), int64(len(raw)))
	if err != nil {
		return fmt.Errorf("解析内置安装包：%w", err)
	}
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return fmt.Errorf("创建安装目录：%w", err)
	}

	for _, f := range zr.File {
		name := filepath.Clean(filepath.FromSlash(f.Name))
		if name == "." || filepath.IsAbs(name) || name == ".." || strings.HasPrefix(name, ".."+string(filepath.Separator)) {
			return fmt.Errorf("安装包包含不安全路径：%q", f.Name)
		}
		dst := filepath.Join(dir, name)
		if f.FileInfo().IsDir() {
			if err := os.MkdirAll(dst, 0o755); err != nil {
				return err
			}
			continue
		}
		if err := os.MkdirAll(filepath.Dir(dst), 0o755); err != nil {
			return err
		}
		if err := writeZipFile(f, dst); err != nil {
			return fmt.Errorf("释放 %s：%w", name, err)
		}
		logf("extracted %s (%d bytes)", name, f.UncompressedSize64)
	}

	exe := filepath.Join(dir, appName+".exe")
	if st, err := os.Stat(exe); err != nil || st.IsDir() {
		return fmt.Errorf("安装包内缺少有效的 %s.exe", appName)
	}
	core := filepath.Join(dir, "core-bin", "aether.exe")
	if st, err := os.Stat(core); err != nil || st.IsDir() {
		return fmt.Errorf("安装包内缺少有效的 Aether 核心：%s", core)
	}
	if err := makeShortcuts(exe, dir); err != nil {
		logf("shortcut creation warning: %v", err)
		// A missing shortcut must not invalidate an otherwise complete install.
	}
	return nil
}

func writeZipFile(f *zip.File, dst string) error {
	rc, err := f.Open()
	if err != nil {
		return err
	}
	defer rc.Close()
	out, err := os.Create(dst)
	if err != nil {
		return err
	}
	if _, err := io.Copy(out, rc); err != nil {
		_ = out.Close()
		return err
	}
	return out.Close()
}

func psQuote(s string) string {
	return strings.ReplaceAll(s, "'", "''")
}

func makeShortcuts(exe, dir string) error {
	exeQ, dirQ := psQuote(exe), psQuote(dir)
	script := "$ErrorActionPreference='Stop'\n" +
		"$ws = New-Object -ComObject WScript.Shell\n" +
		"$desk = $ws.SpecialFolders('Desktop')\n" +
		"$s = $ws.CreateShortcut((Join-Path $desk '卡卡.lnk'))\n" +
		"$s.TargetPath = '" + exeQ + "'\n" +
		"$s.WorkingDirectory = '" + dirQ + "'\n" +
		"$s.IconLocation = '" + exeQ + ",0'\n" +
		"$s.Save()\n" +
		"$sm = Join-Path ([Environment]::GetFolderPath('StartMenu')) 'Programs/卡卡'\n" +
		"New-Item -ItemType Directory -Force -Path $sm | Out-Null\n" +
		"$s2 = $ws.CreateShortcut((Join-Path $sm '卡卡.lnk'))\n" +
		"$s2.TargetPath = '" + exeQ + "'\n" +
		"$s2.WorkingDirectory = '" + dirQ + "'\n" +
		"$s2.IconLocation = '" + exeQ + ",0'\n" +
		"$s2.Save()\n"
	cmd := exec.Command("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", script)
	if out, err := cmd.CombinedOutput(); err != nil {
		return fmt.Errorf("创建快捷方式失败：%v %s", err, strings.TrimSpace(string(out)))
	}
	return nil
}

