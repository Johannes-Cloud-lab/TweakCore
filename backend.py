import os
import platform
import socket
import subprocess
import time
import json
import shutil


class TweakCoreBackend:

    # =====================================================
    # SYSTEM INFORMATION
    # =====================================================

    def get_system_info(self):
        return {
            "system": "Linux",
            "hostname": socket.gethostname(),
            "kernel": platform.release(),
            "architecture": platform.machine(),
            "distribution": self.get_distribution(),
            "python": platform.python_version(),
            "cpu_cores": os.cpu_count() or 0
        }

    # =====================================================
    # LINUX DISTRIBUTION
    # =====================================================

    def get_distribution(self):
        try:
            if os.path.exists("/etc/os-release"):
                data = {}

                with open("/etc/os-release", "r") as file:
                    for line in file:
                        if "=" in line:
                            key, value = line.strip().split("=", 1)
                            data[key] = value.strip('"')

                return data.get("PRETTY_NAME", "Linux")

        except Exception:
            pass

        return "Linux"

    # =====================================================
    # CPU
    # =====================================================

    def get_cpu_info(self):
        model = "Unknown"

        try:
            with open("/proc/cpuinfo", "r") as file:
                for line in file:
                    if "model name" in line:
                        model = line.split(":", 1)[1].strip()
                        break

                    if "Hardware" in line:
                        model = line.split(":", 1)[1].strip()
                        break
        except Exception:
            pass

        return {
            "model": model,
            "cores": os.cpu_count() or 0,
            "usage_percent": self.get_cpu_usage()
        }

    # =====================================================
    # CPU USAGE
    # =====================================================

    def _read_cpu_times(self):
        with open("/proc/stat", "r") as file:
            line = file.readline()

        values = line.split()[1:]

        values = [int(value) for value in values]

        idle = values[3]

        if len(values) > 4:
            idle += values[4]

        total = sum(values)

        return total, idle

    def get_cpu_usage(self):
        try:
            total1, idle1 = self._read_cpu_times()

            time.sleep(0.1)

            total2, idle2 = self._read_cpu_times()

            total_delta = total2 - total1
            idle_delta = idle2 - idle1

            if total_delta <= 0:
                return 0.0

            usage = (
                1 -
                (idle_delta / total_delta)
            ) * 100

            return round(
                max(0, min(100, usage)),
                1
            )

        except Exception:
            return -1

    # =====================================================
    # RAM
    # =====================================================

    def get_memory_info(self):
        try:
            memory = {}

            with open("/proc/meminfo", "r") as file:
                for line in file:
                    parts = line.split()

                    if len(parts) >= 2:
                        key = parts[0].rstrip(":")
                        value = int(parts[1])

                        memory[key] = value

            total_kb = memory.get(
                "MemTotal",
                0
            )

            available_kb = memory.get(
                "MemAvailable",
                memory.get("MemFree", 0)
            )

            used_kb = (
                total_kb -
                available_kb
            )

            usage = (
                used_kb /
                total_kb *
                100
                if total_kb > 0
                else 0
            )

            return {
                "total_mb": round(
                    total_kb / 1024,
                    1
                ),

                "used_mb": round(
                    used_kb / 1024,
                    1
                ),

                "available_mb": round(
                    available_kb / 1024,
                    1
                ),

                "usage_percent": round(
                    usage,
                    1
                )
            }

        except Exception:
            return {
                "total_mb": 0,
                "used_mb": 0,
                "available_mb": 0,
                "usage_percent": -1
            }

    # =====================================================
    # GPU
    # =====================================================

    def get_gpu_info(self):
        gpus = []

        try:
            if shutil.which("lspci"):
                output = subprocess.check_output(
                    ["lspci"],
                    text=True,
                    stderr=subprocess.DEVNULL
                )

                for line in output.splitlines():

                    if any(
                        word in line.lower()
                        for word in [
                            "vga compatible controller",
                            "3d controller",
                            "display controller"
                        ]
                    ):
                        gpus.append(line.strip())

        except Exception:
            pass

        return {
            "available": len(gpus) > 0,
            "devices": gpus
        }

    # =====================================================
    # BATTERY
    # =====================================================

    def get_battery_info(self):
        batteries = []

        power_path = "/sys/class/power_supply"

        try:
            if os.path.exists(power_path):

                for name in os.listdir(power_path):

                    path = os.path.join(
                        power_path,
                        name
                    )

                    type_file = os.path.join(
                        path,
                        "type"
                    )

                    if not os.path.exists(type_file):
                        continue

                    with open(type_file) as file:
                        battery_type = file.read().strip()

                    if battery_type != "Battery":
                        continue

                    capacity_file = os.path.join(
                        path,
                        "capacity"
                    )

                    status_file = os.path.join(
                        path,
                        "status"
                    )

                    capacity = -1
                    status = "Unknown"

                    if os.path.exists(capacity_file):
                        with open(capacity_file) as file:
                            capacity = int(
                                file.read().strip()
                            )

                    if os.path.exists(status_file):
                        with open(status_file) as file:
                            status = file.read().strip()

                    batteries.append({
                        "name": name,
                        "percent": capacity,
                        "status": status
                    })

        except Exception:
            pass

        return {
            "available": len(batteries) > 0,
            "batteries": batteries
        }

    # =====================================================
    # TEMPERATURE
    # =====================================================

    def get_temperature(self):
        temperatures = []

        thermal_path = "/sys/class/thermal"

        try:

            if not os.path.exists(thermal_path):
                return temperatures

            for zone in os.listdir(thermal_path):

                if not zone.startswith("thermal_zone"):
                    continue

                zone_path = os.path.join(
                    thermal_path,
                    zone
                )

                temp_file = os.path.join(
                    zone_path,
                    "temp"
                )

                type_file = os.path.join(
                    zone_path,
                    "type"
                )

                if not os.path.exists(temp_file):
                    continue

                with open(temp_file) as file:
                    raw = int(
                        file.read().strip()
                    )

                temperature = raw / 1000

                sensor_type = "Unknown"

                if os.path.exists(type_file):
                    with open(type_file) as file:
                        sensor_type = file.read().strip()

                temperatures.append({
                    "sensor": sensor_type,
                    "temperature": round(
                        temperature,
                        1
                    )
                })

        except Exception:
            pass

        return temperatures

    # =====================================================
    # DISPLAY
    # =====================================================

    def get_display_info(self):
        displays = []

        try:

            if shutil.which("xrandr"):

                output = subprocess.check_output(
                    ["xrandr", "--current"],
                    text=True,
                    stderr=subprocess.DEVNULL
                )

                for line in output.splitlines():

                    if " connected" not in line:
                        continue

                    displays.append(
                        line.strip()
                    )

        except Exception:
            pass

        return {
            "available": len(displays) > 0,
            "displays": displays
        }

    # =====================================================
    # NETWORK
    # =====================================================

    def get_network_info(self):

        interfaces = []

        try:

            for interface in os.listdir(
                "/sys/class/net"
            ):

                if interface == "lo":
                    continue

                state_file = (
                    f"/sys/class/net/"
                    f"{interface}/operstate"
                )

                state = "unknown"

                if os.path.exists(state_file):

                    with open(state_file) as file:
                        state = file.read().strip()

                interfaces.append({
                    "name": interface,
                    "state": state
                })

        except Exception:
            pass

        connected = any(
            interface["state"] == "up"
            for interface in interfaces
        )

        return {
            "connected": connected,
            "interfaces": interfaces
        }

    # =====================================================
    # PING
    # =====================================================

    def ping(
        self,
        host="1.1.1.1"
    ):

        if not shutil.which("ping"):

            return {
                "success": False,
                "milliseconds": -1,
                "status": "ping command unavailable"
            }

        try:

            start = time.perf_counter()

            result = subprocess.run(
                [
                    "ping",
                    "-c",
                    "1",
                    "-W",
                    "1",
                    host
                ],
                capture_output=True,
                text=True
            )

            elapsed = (
                time.perf_counter() -
                start
            )

            success = (
                result.returncode == 0
            )

            return {
                "host": host,
                "success": success,
                "milliseconds":
                    round(
                        elapsed * 1000,
                        1
                    )
                if success else -1
            }

        except Exception as error:

            return {
                "host": host,
                "success": False,
                "milliseconds": -1,
                "error": str(error)
            }

    # =====================================================
    # LINUX VERSION
    # =====================================================

    def get_linux_info(self):

        return {
            "distribution":
                self.get_distribution(),

            "kernel":
                platform.release(),

            "version":
                platform.version(),

            "architecture":
                platform.machine()
        }

    # =====================================================
    # PERFORMANCE PROFILE
    # =====================================================

    def get_performance_profile(self):

        cpu = self.get_cpu_usage()

        memory = self.get_memory_info()

        ram = memory.get(
            "usage_percent",
            0
        )

        if cpu >= 80 or ram >= 90:

            profile = "HIGH_LOAD"

        elif cpu >= 40 or ram >= 60:

            profile = "BALANCED"

        else:

            profile = "LOW_LOAD"

        return {
            "profile": profile,
            "cpu_usage": cpu,
            "ram_usage": ram
        }

    # =====================================================
    # FULL DASHBOARD
    # =====================================================

    def get_dashboard_data(self):

        return {
            "system":
                self.get_system_info(),

            "linux":
                self.get_linux_info(),

            "cpu":
                self.get_cpu_info(),

            "memory":
                self.get_memory_info(),

            "gpu":
                self.get_gpu_info(),

            "battery":
                self.get_battery_info(),

            "temperature":
                self.get_temperature(),

            "display":
                self.get_display_info(),

            "network":
                self.get_network_info(),

            "ping":
                self.ping(),

            "performance":
                self.get_performance_profile()
        }

    # =====================================================
    # STATUS
    # =====================================================

    def get_status(self):

        return {
            "backend":
                "TweakCore Linux",

            "running":
                True,

            "dangerous_tweaks":
                False,

            "system_modification":
                False,

            "timestamp":
                time.time()
        }


# =========================================================
# SIMPLE TEST
# =========================================================

if __name__ == "__main__":

    backend = TweakCoreBackend()

    print(
        json.dumps(
            backend.get_dashboard_data(),
            indent=4
        )
    )