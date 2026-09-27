using System;
using System.Diagnostics;
using System.Management;
using System.Net.NetworkInformation;
using System.Runtime.InteropServices;
using System.Text.Json;

namespace TweakCore.Windows
{
    public class Backend
    {
        // =====================================================
        // SYSTEM INFORMATION
        // =====================================================

        public string GetSystemInfo()
        {
            var result = new
            {
                system = "Windows",
                computerName = Environment.MachineName,
                userName = Environment.UserName,
                os = Environment.OSVersion.VersionString,
                architecture = Environment.Is64BitOperatingSystem
                    ? "x64"
                    : "x86",
                processor = Environment.GetEnvironmentVariable("PROCESSOR_IDENTIFIER")
                    ?? "Unknown",
                cpuCores = Environment.ProcessorCount,
                dotnet = Environment.Version.ToString()
            };

            return JsonSerializer.Serialize(result);
        }

        // =====================================================
        // CPU
        // =====================================================

        public string GetCpuInfo()
        {
            string name = "Unknown";
            string manufacturer = "Unknown";
            int cores = Environment.ProcessorCount;
            int logicalProcessors = Environment.ProcessorCount;

            try
            {
                using var searcher =
                    new ManagementObjectSearcher(
                        "SELECT Name, Manufacturer, NumberOfCores, NumberOfLogicalProcessors FROM Win32_Processor");

                foreach (ManagementObject cpu in searcher.Get())
                {
                    name = cpu["Name"]?.ToString() ?? "Unknown";
                    manufacturer = cpu["Manufacturer"]?.ToString() ?? "Unknown";

                    if (cpu["NumberOfCores"] != null)
                        cores = Convert.ToInt32(cpu["NumberOfCores"]);

                    if (cpu["NumberOfLogicalProcessors"] != null)
                        logicalProcessors =
                            Convert.ToInt32(cpu["NumberOfLogicalProcessors"]);

                    break;
                }
            }
            catch
            {
                // Fallback auf .NET-Werte
            }

            var result = new
            {
                name,
                manufacturer,
                cores,
                logicalProcessors,
                usagePercent = GetCpuUsage()
            };

            return JsonSerializer.Serialize(result);
        }

        // =====================================================
        // CPU USAGE
        // =====================================================

        public double GetCpuUsage()
        {
            try
            {
                using var cpu =
                    new PerformanceCounter(
                        "Processor",
                        "% Processor Time",
                        "_Total");

                cpu.NextValue();

                System.Threading.Thread.Sleep(100);

                return Math.Round(
                    cpu.NextValue(),
                    1);
            }
            catch
            {
                return -1;
            }
        }

        // =====================================================
        // RAM
        // =====================================================

        public string GetMemoryInfo()
        {
            try
            {
                using var searcher =
                    new ManagementObjectSearcher(
                        "SELECT TotalVisibleMemorySize, FreePhysicalMemory FROM Win32_OperatingSystem");

                foreach (ManagementObject item in searcher.Get())
                {
                    long total =
                        Convert.ToInt64(
                            item["TotalVisibleMemorySize"]);

                    long free =
                        Convert.ToInt64(
                            item["FreePhysicalMemory"]);

                    long totalMb = total / 1024;
                    long freeMb = free / 1024;
                    long usedMb = totalMb - freeMb;

                    int usage =
                        totalMb > 0
                            ? (int)((usedMb * 100) / totalMb)
                            : 0;

                    var result = new
                    {
                        totalMb,
                        usedMb,
                        freeMb,
                        usagePercent = usage
                    };

                    return JsonSerializer.Serialize(result);
                }
            }
            catch
            {
            }

            return JsonSerializer.Serialize(
                new
                {
                    totalMb = 0,
                    usedMb = 0,
                    freeMb = 0,
                    usagePercent = -1
                });
        }

        // =====================================================
        // GPU
        // =====================================================

        public string GetGpuInfo()
        {
            string name = "Unknown";
            string driver = "Unknown";
            string memory = "Unknown";

            try
            {
                using var searcher =
                    new ManagementObjectSearcher(
                        "SELECT Name, DriverVersion, AdapterRAM FROM Win32_VideoController");

                foreach (ManagementObject gpu in searcher.Get())
                {
                    name =
                        gpu["Name"]?.ToString()
                        ?? "Unknown";

                    driver =
                        gpu["DriverVersion"]?.ToString()
                        ?? "Unknown";

                    if (gpu["AdapterRAM"] != null)
                    {
                        long bytes =
                            Convert.ToInt64(
                                gpu["AdapterRAM"]);

                        memory =
                            Math.Round(
                                bytes / 1024.0 / 1024.0 / 1024.0,
                                2) + " GB";
                    }

                    break;
                }
            }
            catch
            {
            }

            var result = new
            {
                name,
                driver,
                memory
            };

            return JsonSerializer.Serialize(result);
        }

        // =====================================================
        // BATTERY
        // =====================================================

        public string GetBatteryInfo()
        {
            try
            {
                using var searcher =
                    new ManagementObjectSearcher(
                        "SELECT EstimatedChargeRemaining, BatteryStatus FROM Win32_Battery");

                foreach (ManagementObject battery in searcher.Get())
                {
                    int level =
                        Convert.ToInt32(
                            battery["EstimatedChargeRemaining"]);

                    int status =
                        Convert.ToInt32(
                            battery["BatteryStatus"]);

                    var result = new
                    {
                        available = true,
                        percent = level,
                        status
                    };

                    return JsonSerializer.Serialize(result);
                }
            }
            catch
            {
            }

            return JsonSerializer.Serialize(
                new
                {
                    available = false,
                    percent = -1,
                    status = -1
                });
        }

        // =====================================================
        // WINDOWS VERSION
        // =====================================================

        public string GetWindowsInfo()
        {
            var result = new
            {
                name = "Windows",
                version = Environment.OSVersion.Version.ToString(),
                description =
                    Environment.OSVersion.VersionString,
                architecture =
                    Environment.Is64BitOperatingSystem
                        ? "64-bit"
                        : "32-bit"
            };

            return JsonSerializer.Serialize(result);
        }

        // =====================================================
        // NETWORK
        // =====================================================

        public string GetNetworkInfo()
        {
            int adapters = 0;
            int connected = 0;

            foreach (
                NetworkInterface network
                in NetworkInterface.GetAllNetworkInterfaces())
            {
                if (
                    network.NetworkInterfaceType ==
                    NetworkInterfaceType.Loopback)
                    continue;

                adapters++;

                if (
                    network.OperationalStatus ==
                    OperationalStatus.Up)
                {
                    connected++;
                }
            }

            var result = new
            {
                connected = connected > 0,
                adapters,
                activeAdapters = connected
            };

            return JsonSerializer.Serialize(result);
        }

        // =====================================================
        // PING
        // =====================================================

        public async System.Threading.Tasks.Task<string>
            PingHostAsync(string host = "1.1.1.1")
        {
            try
            {
                using var ping = new Ping();

                PingReply reply =
                    await ping.SendPingAsync(
                        host,
                        3000);

                var result = new
                {
                    host,
                    success =
                        reply.Status ==
                        IPStatus.Success,
                    milliseconds =
                        reply.Status ==
                        IPStatus.Success
                            ? reply.RoundtripTime
                            : -1,
                    status =
                        reply.Status.ToString()
                };

                return JsonSerializer.Serialize(result);
            }
            catch (Exception ex)
            {
                return JsonSerializer.Serialize(
                    new
                    {
                        host,
                        success = false,
                        milliseconds = -1,
                        status = ex.Message
                    });
            }
        }

        // =====================================================
        // DISPLAY
        // =====================================================

        public string GetDisplayInfo()
        {
            try
            {
                using var searcher =
                    new ManagementObjectSearcher(
                        "SELECT Name, CurrentHorizontalResolution, CurrentVerticalResolution, CurrentRefreshRate FROM Win32_VideoController");

                foreach (ManagementObject display in searcher.Get())
                {
                    var result = new
                    {
                        name =
                            display["Name"]?.ToString()
                            ?? "Unknown",

                        width =
                            display["CurrentHorizontalResolution"]
                            ?.ToString()
                            ?? "Unknown",

                        height =
                            display["CurrentVerticalResolution"]
                            ?.ToString()
                            ?? "Unknown",

                        refreshRate =
                            display["CurrentRefreshRate"]
                            ?.ToString()
                            ?? "Unknown"
                    };

                    return JsonSerializer.Serialize(result);
                }
            }
            catch
            {
            }

            return JsonSerializer.Serialize(
                new
                {
                    name = "Unknown",
                    width = "Unknown",
                    height = "Unknown",
                    refreshRate = "Unknown"
                });
        }

        // =====================================================
        // PERFORMANCE PROFILE
        // =====================================================

        public string GetPerformanceProfile()
        {
            double cpu = GetCpuUsage();

            string profile;

            if (cpu < 40)
                profile = "LOW";
            else if (cpu < 75)
                profile = "BALANCED";
            else
                profile = "HIGH";

            var result = new
            {
                profile,
                cpuUsage = cpu
            };

            return JsonSerializer.Serialize(result);
        }

        // =====================================================
        // FULL DASHBOARD
        // =====================================================

        public async System.Threading.Tasks.Task<string>
            GetDashboardDataAsync()
        {
            var result = new
            {
                system =
                    JsonSerializer.Deserialize<object>(
                        GetSystemInfo()),

                cpu =
                    JsonSerializer.Deserialize<object>(
                        GetCpuInfo()),

                memory =
                    JsonSerializer.Deserialize<object>(
                        GetMemoryInfo()),

                gpu =
                    JsonSerializer.Deserialize<object>(
                        GetGpuInfo()),

                battery =
                    JsonSerializer.Deserialize<object>(
                        GetBatteryInfo()),

                windows =
                    JsonSerializer.Deserialize<object>(
                        GetWindowsInfo()),

                network =
                    JsonSerializer.Deserialize<object>(
                        GetNetworkInfo()),

                display =
                    JsonSerializer.Deserialize<object>(
                        GetDisplayInfo()),

                performance =
                    JsonSerializer.Deserialize<object>(
                        GetPerformanceProfile()),

                ping =
                    JsonSerializer.Deserialize<object>(
                        await PingHostAsync())
            };

            return JsonSerializer.Serialize(result);
        }

        // =====================================================
        // SAFE SYSTEM STATUS
        // =====================================================

        public string GetStatus()
        {
            var result = new
            {
                backend = "TweakCore Windows",
                running = true,
                administratorRequired = false,
                dangerousTweaks = false,
                systemModification = false,
                timestamp = DateTime.Now
            };

            return JsonSerializer.Serialize(result);
        }
    }
}