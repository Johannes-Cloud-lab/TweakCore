import Foundation
import IOKit
import Network
import Darwin

final class TweakCoreBackend {

    // =====================================================
    // SYSTEM INFORMATION
    // =====================================================

    func getSystemInfo() -> [String: Any] {
        return [
            "system": "macOS",
            "computerName": Host.current().localizedName ?? "Mac",
            "osVersion": ProcessInfo.processInfo.operatingSystemVersionString,
            "architecture": architecture(),
            "cpuCores": ProcessInfo.processInfo.processorCount,
            "physicalCores": ProcessInfo.processInfo.activeProcessorCount
        ]
    }

    // =====================================================
    // CPU
    // =====================================================

    func getCPUInfo() -> [String: Any] {

        let physical =
            ProcessInfo.processInfo.processorCount

        let active =
            ProcessInfo.processInfo.activeProcessorCount

        return [
            "cores": physical,
            "activeCores": active,
            "usagePercent": getCPUUsage()
        ]
    }

    func getCPUUsage() -> Double {

        var cpuInfo: processor_info_array_t?
        var numCpuInfo: mach_msg_type_number_t = 0
        var numCpu: natural_t = 0

        let result = host_processor_info(
            mach_host_self(),
            PROCESSOR_CPU_LOAD_INFO,
            &numCpu,
            &cpuInfo,
            &numCpuInfo
        )

        if result != KERN_SUCCESS {
            return -1
        }

        guard let cpuInfo = cpuInfo else {
            return -1
        }

        var totalUser: UInt32 = 0
        var totalSystem: UInt32 = 0
        var totalIdle: UInt32 = 0
        var totalNice: UInt32 = 0

        for cpu in 0..<Int(numCpu) {

            let offset =
                Int(CPU_STATE_MAX) * cpu

            totalUser +=
                cpuInfo[offset + Int(CPU_STATE_USER)]

            totalSystem +=
                cpuInfo[offset + Int(CPU_STATE_SYSTEM)]

            totalIdle +=
                cpuInfo[offset + Int(CPU_STATE_IDLE)]

            totalNice +=
                cpuInfo[offset + Int(CPU_STATE_NICE)]
        }

        let total =
            totalUser +
            totalSystem +
            totalIdle +
            totalNice

        if total == 0 {
            return 0
        }

        let used =
            total -
            totalIdle

        return Double(used) /
            Double(total) *
            100
    }

    // =====================================================
    // RAM
    // =====================================================

    func getMemoryInfo() -> [String: Any] {

        let total =
            ProcessInfo.processInfo.physicalMemory

        let used =
            getUsedMemory()

        let totalGB =
            Double(total) /
            1024 /
            1024 /
            1024

        let usedGB =
            Double(used) /
            1024 /
            1024 /
            1024

        let percentage =
            total > 0
            ? Double(used) /
              Double(total) *
              100
            : 0

        return [
            "totalGB":
                round(totalGB * 100) / 100,

            "usedGB":
                round(usedGB * 100) / 100,

            "usagePercent":
                round(percentage * 10) / 10
        ]
    }

    private func getUsedMemory() -> UInt64 {

        var stats =
            vm_statistics64_data_t()

        var count =
            mach_msg_type_number_t(
                MemoryLayout<vm_statistics64_data_t>
                    .size /
                MemoryLayout<integer_t>
                    .size
            )

        let result =
            withUnsafeMutablePointer(to: &stats) {

                $0.withMemoryRebound(
                    to: integer_t.self,
                    capacity: Int(count)
                ) {

                    host_statistics64(
                        mach_host_self(),
                        HOST_VM_INFO64,
                        $0,
                        &count
                    )
                }
            }

        if result != KERN_SUCCESS {
            return 0
        }

        let usedPages =
            UInt64(stats.active_count) +
            UInt64(stats.wire_count) +
            UInt64(stats.compressed_count)

        return usedPages *
            UInt64(vm_page_size)
    }

    // =====================================================
    // BATTERY
    // =====================================================

    func getBatteryInfo() -> [String: Any] {

        let output =
            shell(
                "/usr/bin/pmset",
                arguments: ["-g", "batt"]
            )

        var percentage = -1

        let pattern =
            #"(\d+)%"#

        if let regex =
            try? NSRegularExpression(
                pattern: pattern
            ) {

            let range =
                NSRange(
                    output.startIndex..<output.endIndex,
                    in: output
                )

            if let match =
                regex.firstMatch(
                    in: output,
                    range: range
                ) {

                if let valueRange =
                    Range(
                        match.range(at: 1),
                        in: output
                    ) {

                    percentage =
                        Int(
                            output[valueRange]
                        ) ?? -1
                }
            }
        }

        return [
            "available":
                percentage >= 0,

            "percent":
                percentage
        ]
    }

    // =====================================================
    // DISPLAY
    // =====================================================

    func getDisplayInfo() -> [String: Any] {

        let output =
            shell(
                "/usr/sbin/system_profiler",
                arguments:
                    ["SPDisplaysDataType"]
            )

        return [
            "available": !output.isEmpty,
            "details": output
        ]
    }

    // =====================================================
    // NETWORK
    // =====================================================

    func getNetworkInfo() -> [String: Any] {

        let output =
            shell(
                "/sbin/ifconfig",
                arguments: []
            )

        let connected =
            output.contains("status: active")

        return [
            "connected":
                connected
        ]
    }

    // =====================================================
    // PING
    // =====================================================

    func ping(
        host: String = "1.1.1.1"
    ) -> [String: Any] {

        let start =
            Date()

        let output =
            shell(
                "/sbin/ping",
                arguments:
                    ["-c", "1", "-W", "1000", host]
            )

        let duration =
            Date().timeIntervalSince(start)

        let success =
            output.contains("1 packets transmitted, 1 packets received")

        return [
            "host":
                host,

            "success":
                success,

            "milliseconds":
                success
                ? Int(duration * 1000)
                : -1
        ]
    }

    // =====================================================
    // macOS VERSION
    // =====================================================

    func getMacOSInfo() -> [String: Any] {

        let version =
            ProcessInfo.processInfo
                .operatingSystemVersion

        return [
            "major":
                version.majorVersion,

            "minor":
                version.minorVersion,

            "patch":
                version.patchVersion,

            "description":
                ProcessInfo.processInfo
                    .operatingSystemVersionString
        ]
    }

    // =====================================================
    // PERFORMANCE PROFILE
    // =====================================================

    func getPerformanceProfile() -> [String: Any] {

        let cpu =
            getCPUUsage()

        let profile: String

        if cpu < 40 {
            profile = "LOW"
        }
        else if cpu < 75 {
            profile = "BALANCED"
        }
        else {
            profile = "HIGH"
        }

        return [
            "profile":
                profile,

            "cpuUsage":
                cpu
        ]
    }

    // =====================================================
    // FULL DASHBOARD
    // =====================================================

    func getDashboardData() -> [String: Any] {

        return [
            "system":
                getSystemInfo(),

            "cpu":
                getCPUInfo(),

            "memory":
                getMemoryInfo(),

            "battery":
                getBatteryInfo(),

            "display":
                getDisplayInfo(),

            "network":
                getNetworkInfo(),

            "macOS":
                getMacOSInfo(),

            "performance":
                getPerformanceProfile(),

            "ping":
                ping()
        ]
    }

    // =====================================================
    // STATUS
    // =====================================================

    func getStatus() -> [String: Any] {

        return [
            "backend":
                "TweakCore macOS",

            "running":
                true,

            "dangerousTweaks":
                false,

            "systemModification":
                false,

            "timestamp":
                Date().timeIntervalSince1970
        ]
    }

    // =====================================================
    // ARCHITECTURE
    // =====================================================

    private func architecture() -> String {

        #if arch(arm64)
        return "Apple Silicon (ARM64)"
        #elseif arch(x86_64)
        return "Intel (x86_64)"
        #else
        return "Unknown"
        #endif
    }

    // =====================================================
    // SHELL
    // =====================================================

    private func shell(
        _ command: String,
        arguments: [String]
    ) -> String {

        let process =
            Process()

        process.executableURL =
            URL(fileURLWithPath: command)

        process.arguments =
            arguments

        let pipe =
            Pipe()

        process.standardOutput =
            pipe

        process.standardError =
            pipe

        do {

            try process.run()

            process.waitUntilExit()

            let data =
                pipe.fileHandleForReading
                    .readDataToEndOfFile()

            return String(
                data: data,
                encoding: .utf8
            ) ?? ""

        } catch {

            return ""
        }
    }
}