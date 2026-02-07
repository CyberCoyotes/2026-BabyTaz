#!/usr/bin/env python3
"""
AprilTag Alignment Analysis Tool

Analyzes AdvantageKit .wpilog files to quantify AprilTag alignment performance.
Metrics include:
  1. Alignment speed (time to settle)
  2. Alignment accuracy (final error)
  3. Overshoot/oscillation
  4. Control stability
  5. Success rate

Usage:
    python analyze_alignment.py <log_file.wpilog> [--model A|B|C|main] [--plot]

Requirements:
    pip install advantagescope-log-parser matplotlib numpy pandas
"""

import argparse
import sys
from pathlib import Path
import numpy as np
import matplotlib.pyplot as plt
from dataclasses import dataclass
from typing import List, Dict, Optional, Tuple
import json

try:
    from advantagescope import LogFile
except ImportError:
    print("ERROR: advantagescope module not found.")
    print("Install with: pip install advantagescope-log-parser")
    sys.exit(1)


@dataclass
class AlignmentMetrics:
    """Metrics for a single alignment attempt"""
    success: bool
    alignment_time: Optional[float]  # Time to reach aligned state (seconds)
    settling_time: Optional[float]   # Time to settle within tolerance (seconds)

    # Final errors at completion
    final_forward_error: Optional[float]  # meters
    final_lateral_error: Optional[float]  # meters
    final_rotation_error: Optional[float]  # degrees

    # Peak errors during alignment
    max_forward_error: float  # meters
    max_lateral_error: float  # meters
    max_rotation_error: float  # degrees

    # Overshoot analysis
    forward_overshoot: float  # percentage
    lateral_overshoot: float  # percentage
    rotation_overshoot: float  # percentage

    # Oscillation count (zero crossings)
    forward_oscillations: int
    lateral_oscillations: int
    rotation_oscillations: int

    # Control effort (average absolute speed)
    avg_forward_speed: float  # m/s
    avg_lateral_speed: float  # m/s
    avg_rotation_speed: float  # rad/s

    # Target tracking
    target_lost_count: int
    target_id: Optional[int]


@dataclass
class AlignmentSession:
    """Complete alignment session data"""
    start_time: float
    end_time: float
    duration: float
    metrics: AlignmentMetrics

    # Time series data
    timestamps: np.ndarray
    forward_error: np.ndarray
    lateral_error: np.ndarray
    rotation_error: np.ndarray
    forward_speed: np.ndarray
    lateral_speed: np.ndarray
    rotation_speed: np.ndarray
    has_target: np.ndarray
    aligned_flags: np.ndarray


class AlignmentAnalyzer:
    """Analyzes AprilTag alignment performance from .wpilog files"""

    def __init__(self, log_path: str, model: str = "main"):
        """
        Initialize analyzer

        Args:
            log_path: Path to .wpilog file
            model: Which alignment model to analyze ("A", "B", "C", "main")
        """
        self.log_path = Path(log_path)
        if not self.log_path.exists():
            raise FileNotFoundError(f"Log file not found: {log_path}")

        self.model = model.upper()
        self.log_file = None
        self.data = {}

        # Define field mappings for different models
        self.field_map = self._get_field_map()

    def _get_field_map(self) -> Dict[str, str]:
        """Get NetworkTables field names based on model"""
        if self.model == "A":
            prefix = "VisionTest/ModelA"
            return {
                "status": f"{prefix}/Status",
                "rotation_error": f"{prefix}/TX",  # Use TX directly as rotation error
                "rotation_speed": f"{prefix}/RotationSpeed",
                "rotation_aligned": f"{prefix}/RotationAtTarget",
                "fully_aligned": f"{prefix}/FullyAligned",
                "tag_id": f"{prefix}/TagID",
            }
        elif self.model == "B":
            prefix = "VisionTest/ModelB"
            return {
                "status": f"{prefix}/Status",
                "rotation_error": f"{prefix}/TX",
                "distance_error": f"{prefix}/DistanceError",
                "rotation_speed": f"{prefix}/RotationSpeed",
                "forward_speed": f"{prefix}/ForwardSpeed",
                "rotation_aligned": f"{prefix}/RotationAtTarget",
                "distance_aligned": f"{prefix}/DistanceAtTarget",
                "fully_aligned": f"{prefix}/FullyAligned",
                "tag_id": f"{prefix}/TagID",
            }
        elif self.model == "C":
            prefix = "VisionTest/ModelC"
            return {
                "status": f"{prefix}/Status",
                "rotation_error": f"{prefix}/TX",
                "distance_error": f"{prefix}/DistanceError",
                "lateral_error": f"{prefix}/TY",  # For Model C, this would need proper lateral offset
                "rotation_speed": f"{prefix}/RotationSpeed",
                "forward_speed": f"{prefix}/ForwardSpeed",
                "lateral_speed": f"{prefix}/LateralSpeed",
                "rotation_aligned": f"{prefix}/RotationAtTarget",
                "distance_aligned": f"{prefix}/DistanceAtTarget",
                "lateral_aligned": f"{prefix}/LateralAtTarget",
                "fully_aligned": f"{prefix}/FullyAligned",
                "tag_id": f"{prefix}/TagID",
            }
        else:  # main
            prefix = "AprilTagAlign"
            return {
                "status": f"{prefix}/Status",
                "forward_error": f"{prefix}/ForwardError",
                "lateral_error": f"{prefix}/LateralError",
                "rotation_error": f"{prefix}/RotationError",
                "forward_speed": f"{prefix}/ForwardSpeed",
                "lateral_speed": f"{prefix}/LateralSpeed",
                "rotation_speed": f"{prefix}/RotationSpeed",
                "forward_aligned": f"{prefix}/ForwardAligned",
                "lateral_aligned": f"{prefix}/LateralAligned",
                "rotation_aligned": f"{prefix}/RotationAligned",
                "fully_aligned": f"{prefix}/FullyAligned",
                "tag_id": f"{prefix}/TagID",
            }

    def load_log(self):
        """Load and parse the log file"""
        print(f"Loading log file: {self.log_path}")
        self.log_file = LogFile(str(self.log_path))

        # Load relevant fields
        print(f"Loading data for model {self.model}...")
        for key, field_name in self.field_map.items():
            try:
                self.data[key] = self.log_file.get(field_name)
                if self.data[key] is not None:
                    print(f"  ✓ {key}: {len(self.data[key])} samples")
                else:
                    print(f"  ✗ {key}: not found")
            except Exception as e:
                print(f"  ✗ {key}: error - {e}")
                self.data[key] = None

        # Also load vision subsystem data
        try:
            self.data['vision_has_target'] = self.log_file.get("Vision/HasTarget")
        except:
            self.data['vision_has_target'] = None

    def find_alignment_sessions(self) -> List[AlignmentSession]:
        """
        Find individual alignment attempts in the log

        Returns:
            List of AlignmentSession objects
        """
        if self.data.get('status') is None:
            print("ERROR: No status data found. Cannot detect alignment sessions.")
            return []

        sessions = []
        status_data = self.data['status']

        # Find transitions to STARTED or ALIGNING
        in_session = False
        session_start_idx = None

        for i in range(len(status_data['timestamps'])):
            status = status_data['values'][i]

            if not in_session and status in ['STARTED', 'ALIGNING']:
                in_session = True
                session_start_idx = i

            elif in_session and status in ['COMPLETED', 'INTERRUPTED']:
                # Session ended
                session = self._extract_session(session_start_idx, i)
                if session:
                    sessions.append(session)
                in_session = False
                session_start_idx = None

        # Handle case where session is still active at end of log
        if in_session and session_start_idx is not None:
            session = self._extract_session(session_start_idx, len(status_data['timestamps']) - 1)
            if session:
                sessions.append(session)

        print(f"\nFound {len(sessions)} alignment session(s)")
        return sessions

    def _extract_session(self, start_idx: int, end_idx: int) -> Optional[AlignmentSession]:
        """Extract data for a single session"""
        status_data = self.data['status']
        start_time = status_data['timestamps'][start_idx]
        end_time = status_data['timestamps'][end_idx]
        duration = end_time - start_time

        if duration < 0.1:  # Ignore very short sessions
            return None

        # Extract time series data within this window
        timestamps = []
        forward_error = []
        lateral_error = []
        rotation_error = []
        forward_speed = []
        lateral_speed = []
        rotation_speed = []
        has_target = []
        aligned_flags = []

        # Helper to get values in time range
        def get_values_in_range(data_dict, key):
            if data_dict.get(key) is None:
                return [], []
            times = np.array(data_dict[key]['timestamps'])
            values = np.array(data_dict[key]['values'])
            mask = (times >= start_time) & (times <= end_time)
            return times[mask], values[mask]

        # Get all data streams
        t_forward_err, forward_error = get_values_in_range(self.data, 'forward_error')
        t_lateral_err, lateral_error = get_values_in_range(self.data, 'lateral_error')
        t_rotation_err, rotation_error = get_values_in_range(self.data, 'rotation_error')
        t_forward_spd, forward_speed = get_values_in_range(self.data, 'forward_speed')
        t_lateral_spd, lateral_speed = get_values_in_range(self.data, 'lateral_speed')
        t_rotation_spd, rotation_speed = get_values_in_range(self.data, 'rotation_speed')
        t_has_target, has_target = get_values_in_range(self.data, 'vision_has_target')
        t_aligned, aligned_flags = get_values_in_range(self.data, 'fully_aligned')

        # Use the most complete timestamp array
        if len(t_forward_err) > 0:
            timestamps = t_forward_err
        elif len(t_rotation_err) > 0:
            timestamps = t_rotation_err
        else:
            return None

        # Interpolate all other signals to common timestamp
        def interp_to_common(t_src, vals, t_dest):
            if len(t_src) == 0 or len(vals) == 0:
                return np.zeros_like(t_dest)
            return np.interp(t_dest, t_src, vals)

        forward_error = interp_to_common(t_forward_err, forward_error, timestamps)
        lateral_error = interp_to_common(t_lateral_err, lateral_error, timestamps)
        rotation_error = interp_to_common(t_rotation_err, rotation_error, timestamps)
        forward_speed = interp_to_common(t_forward_spd, forward_speed, timestamps)
        lateral_speed = interp_to_common(t_lateral_spd, lateral_speed, timestamps)
        rotation_speed = interp_to_common(t_rotation_spd, rotation_speed, timestamps)
        has_target = interp_to_common(t_has_target, has_target.astype(float), timestamps) > 0.5
        aligned_flags = interp_to_common(t_aligned, aligned_flags.astype(float), timestamps) > 0.5

        # Calculate metrics
        metrics = self._calculate_metrics(
            timestamps - start_time,  # Relative time
            forward_error,
            lateral_error,
            rotation_error,
            forward_speed,
            lateral_speed,
            rotation_speed,
            has_target,
            aligned_flags
        )

        return AlignmentSession(
            start_time=start_time,
            end_time=end_time,
            duration=duration,
            metrics=metrics,
            timestamps=timestamps - start_time,
            forward_error=forward_error,
            lateral_error=lateral_error,
            rotation_error=rotation_error,
            forward_speed=forward_speed,
            lateral_speed=lateral_speed,
            rotation_speed=rotation_speed,
            has_target=has_target,
            aligned_flags=aligned_flags
        )

    def _calculate_metrics(
        self,
        timestamps: np.ndarray,
        forward_error: np.ndarray,
        lateral_error: np.ndarray,
        rotation_error: np.ndarray,
        forward_speed: np.ndarray,
        lateral_speed: np.ndarray,
        rotation_speed: np.ndarray,
        has_target: np.ndarray,
        aligned_flags: np.ndarray
    ) -> AlignmentMetrics:
        """Calculate performance metrics from time series data"""

        # Success detection
        success = np.any(aligned_flags)

        # Alignment time (first time fully aligned is reached)
        alignment_time = None
        if success:
            aligned_indices = np.where(aligned_flags)[0]
            if len(aligned_indices) > 0:
                alignment_time = timestamps[aligned_indices[0]]

        # Settling time (time to stay within tolerance for 0.5s)
        settling_time = self._calculate_settling_time(
            timestamps, forward_error, lateral_error, rotation_error,
            forward_tol=0.10, lateral_tol=0.05, rotation_tol=2.0
        )

        # Final errors
        final_forward_error = forward_error[-1] if len(forward_error) > 0 else None
        final_lateral_error = lateral_error[-1] if len(lateral_error) > 0 else None
        final_rotation_error = rotation_error[-1] if len(rotation_error) > 0 else None

        # Peak errors
        max_forward_error = np.max(np.abs(forward_error)) if len(forward_error) > 0 else 0
        max_lateral_error = np.max(np.abs(lateral_error)) if len(lateral_error) > 0 else 0
        max_rotation_error = np.max(np.abs(rotation_error)) if len(rotation_error) > 0 else 0

        # Overshoot calculation
        forward_overshoot = self._calculate_overshoot(forward_error)
        lateral_overshoot = self._calculate_overshoot(lateral_error)
        rotation_overshoot = self._calculate_overshoot(rotation_error)

        # Oscillation counting (zero crossings)
        forward_oscillations = self._count_zero_crossings(forward_error)
        lateral_oscillations = self._count_zero_crossings(lateral_error)
        rotation_oscillations = self._count_zero_crossings(rotation_error)

        # Average control effort
        avg_forward_speed = np.mean(np.abs(forward_speed)) if len(forward_speed) > 0 else 0
        avg_lateral_speed = np.mean(np.abs(lateral_speed)) if len(lateral_speed) > 0 else 0
        avg_rotation_speed = np.mean(np.abs(rotation_speed)) if len(rotation_speed) > 0 else 0

        # Target tracking
        target_lost_count = self._count_target_losses(has_target)

        return AlignmentMetrics(
            success=success,
            alignment_time=alignment_time,
            settling_time=settling_time,
            final_forward_error=final_forward_error,
            final_lateral_error=final_lateral_error,
            final_rotation_error=final_rotation_error,
            max_forward_error=max_forward_error,
            max_lateral_error=max_lateral_error,
            max_rotation_error=max_rotation_error,
            forward_overshoot=forward_overshoot,
            lateral_overshoot=lateral_overshoot,
            rotation_overshoot=rotation_overshoot,
            forward_oscillations=forward_oscillations,
            lateral_oscillations=lateral_oscillations,
            rotation_oscillations=rotation_oscillations,
            avg_forward_speed=avg_forward_speed,
            avg_lateral_speed=avg_lateral_speed,
            avg_rotation_speed=avg_rotation_speed,
            target_lost_count=target_lost_count,
            target_id=None
        )

    def _calculate_settling_time(
        self, timestamps, forward_err, lateral_err, rotation_err,
        forward_tol, lateral_tol, rotation_tol, settle_duration=0.5
    ) -> Optional[float]:
        """Calculate settling time (time to reach and stay within tolerance)"""

        # Find where all errors are within tolerance
        within_tol = (
            (np.abs(forward_err) < forward_tol) &
            (np.abs(lateral_err) < lateral_tol) &
            (np.abs(rotation_err) < rotation_tol)
        )

        if not np.any(within_tol):
            return None

        # Find first index where it stays within tolerance for settle_duration
        for i in range(len(timestamps)):
            if within_tol[i]:
                # Check if it stays settled
                time_at_i = timestamps[i]
                future_mask = timestamps >= (time_at_i + settle_duration)
                if np.any(future_mask):
                    future_settled = within_tol[future_mask]
                    if np.all(future_settled):
                        return timestamps[i]

        return None

    def _calculate_overshoot(self, error: np.ndarray) -> float:
        """Calculate overshoot percentage"""
        if len(error) < 2:
            return 0.0

        initial_error = error[0]
        if abs(initial_error) < 0.001:  # Avoid division by zero
            return 0.0

        # Find max error in opposite direction
        if initial_error > 0:
            overshoot = np.min(error)
            if overshoot < 0:
                return abs(overshoot / initial_error) * 100
        else:
            overshoot = np.max(error)
            if overshoot > 0:
                return abs(overshoot / initial_error) * 100

        return 0.0

    def _count_zero_crossings(self, signal: np.ndarray) -> int:
        """Count number of times signal crosses zero"""
        if len(signal) < 2:
            return 0
        return np.sum(np.diff(np.sign(signal)) != 0)

    def _count_target_losses(self, has_target: np.ndarray) -> int:
        """Count number of times target was lost"""
        if len(has_target) < 2:
            return 0
        # Count transitions from True to False
        return np.sum((has_target[:-1] == True) & (has_target[1:] == False))

    def plot_session(self, session: AlignmentSession, save_path: Optional[str] = None):
        """Plot alignment session data"""
        fig, axes = plt.subplots(3, 1, figsize=(12, 10), sharex=True)

        t = session.timestamps

        # Error plot
        ax = axes[0]
        ax.plot(t, session.forward_error, label='Forward Error (m)', linewidth=2)
        ax.plot(t, session.lateral_error, label='Lateral Error (m)', linewidth=2)
        ax.plot(t, session.rotation_error / 10, label='Rotation Error (deg/10)', linewidth=2)
        ax.axhline(0, color='k', linestyle='--', alpha=0.3)
        ax.set_ylabel('Error')
        ax.legend(loc='upper right')
        ax.grid(True, alpha=0.3)
        ax.set_title(f'Alignment Performance (Duration: {session.duration:.2f}s)')

        # Speed plot
        ax = axes[1]
        ax.plot(t, session.forward_speed, label='Forward Speed (m/s)', linewidth=2)
        ax.plot(t, session.lateral_speed, label='Lateral Speed (m/s)', linewidth=2)
        ax.plot(t, session.rotation_speed, label='Rotation Speed (rad/s)', linewidth=2)
        ax.axhline(0, color='k', linestyle='--', alpha=0.3)
        ax.set_ylabel('Speed')
        ax.legend(loc='upper right')
        ax.grid(True, alpha=0.3)

        # Status plot
        ax = axes[2]
        ax.fill_between(t, 0, session.has_target.astype(float),
                        alpha=0.3, label='Has Target', step='post')
        ax.fill_between(t, 0, session.aligned_flags.astype(float),
                        alpha=0.5, label='Fully Aligned', step='post', color='green')
        ax.set_ylabel('Status')
        ax.set_xlabel('Time (s)')
        ax.legend(loc='upper right')
        ax.grid(True, alpha=0.3)
        ax.set_ylim([-0.1, 1.1])

        plt.tight_layout()

        if save_path:
            plt.savefig(save_path, dpi=150, bbox_inches='tight')
            print(f"Plot saved to: {save_path}")
        else:
            plt.show()

    def print_metrics(self, session: AlignmentSession, session_num: int = 1):
        """Print metrics for a session in readable format"""
        m = session.metrics

        print(f"\n{'='*70}")
        print(f"Session {session_num} Metrics")
        print(f"{'='*70}")
        print(f"Duration: {session.duration:.3f} s")
        print(f"Success: {'✓' if m.success else '✗'}")

        print(f"\n--- Timing ---")
        if m.alignment_time:
            print(f"Time to Aligned: {m.alignment_time:.3f} s")
        else:
            print(f"Time to Aligned: N/A (never reached)")

        if m.settling_time:
            print(f"Settling Time: {m.settling_time:.3f} s")
        else:
            print(f"Settling Time: N/A (never settled)")

        print(f"\n--- Final Errors ---")
        if m.final_forward_error is not None:
            print(f"Forward: {m.final_forward_error:.4f} m ({m.final_forward_error*100:.2f} cm)")
        if m.final_lateral_error is not None:
            print(f"Lateral: {m.final_lateral_error:.4f} m ({m.final_lateral_error*100:.2f} cm)")
        if m.final_rotation_error is not None:
            print(f"Rotation: {m.final_rotation_error:.4f} deg")

        print(f"\n--- Peak Errors ---")
        print(f"Forward: {m.max_forward_error:.4f} m")
        print(f"Lateral: {m.max_lateral_error:.4f} m")
        print(f"Rotation: {m.max_rotation_error:.4f} deg")

        print(f"\n--- Overshoot ---")
        print(f"Forward: {m.forward_overshoot:.1f}%")
        print(f"Lateral: {m.lateral_overshoot:.1f}%")
        print(f"Rotation: {m.rotation_overshoot:.1f}%")

        print(f"\n--- Oscillations (zero crossings) ---")
        print(f"Forward: {m.forward_oscillations}")
        print(f"Lateral: {m.lateral_oscillations}")
        print(f"Rotation: {m.rotation_oscillations}")

        print(f"\n--- Average Control Effort ---")
        print(f"Forward Speed: {m.avg_forward_speed:.4f} m/s")
        print(f"Lateral Speed: {m.avg_lateral_speed:.4f} m/s")
        print(f"Rotation Speed: {m.avg_rotation_speed:.4f} rad/s")

        print(f"\n--- Target Tracking ---")
        print(f"Target Lost Count: {m.target_lost_count}")

        print(f"{'='*70}\n")

    def export_metrics_json(self, sessions: List[AlignmentSession], output_path: str):
        """Export metrics to JSON for further analysis"""
        data = {
            "log_file": str(self.log_path),
            "model": self.model,
            "sessions": []
        }

        for i, session in enumerate(sessions):
            m = session.metrics
            data["sessions"].append({
                "session_num": i + 1,
                "start_time": session.start_time,
                "end_time": session.end_time,
                "duration": session.duration,
                "success": m.success,
                "alignment_time": m.alignment_time,
                "settling_time": m.settling_time,
                "final_errors": {
                    "forward": m.final_forward_error,
                    "lateral": m.final_lateral_error,
                    "rotation": m.final_rotation_error
                },
                "peak_errors": {
                    "forward": m.max_forward_error,
                    "lateral": m.max_lateral_error,
                    "rotation": m.max_rotation_error
                },
                "overshoot": {
                    "forward": m.forward_overshoot,
                    "lateral": m.lateral_overshoot,
                    "rotation": m.rotation_overshoot
                },
                "oscillations": {
                    "forward": m.forward_oscillations,
                    "lateral": m.lateral_oscillations,
                    "rotation": m.rotation_oscillations
                },
                "avg_speeds": {
                    "forward": m.avg_forward_speed,
                    "lateral": m.avg_lateral_speed,
                    "rotation": m.avg_rotation_speed
                },
                "target_lost_count": m.target_lost_count
            })

        with open(output_path, 'w') as f:
            json.dump(data, f, indent=2)

        print(f"Metrics exported to: {output_path}")


def main():
    parser = argparse.ArgumentParser(
        description="Analyze AprilTag alignment performance from .wpilog files"
    )
    parser.add_argument("log_file", help="Path to .wpilog file")
    parser.add_argument(
        "--model",
        choices=["A", "B", "C", "main"],
        default="main",
        help="Alignment model to analyze (A=rotation only, B=rotation+range, C=3-axis, main=AlignToAprilTagCommand)"
    )
    parser.add_argument(
        "--plot",
        action="store_true",
        help="Generate plots for each session"
    )
    parser.add_argument(
        "--export",
        help="Export metrics to JSON file"
    )
    parser.add_argument(
        "--save-plots",
        help="Directory to save plots (instead of displaying)"
    )

    args = parser.parse_args()

    # Create analyzer
    analyzer = AlignmentAnalyzer(args.log_file, model=args.model)

    # Load log
    analyzer.load_log()

    # Find and analyze sessions
    sessions = analyzer.find_alignment_sessions()

    if not sessions:
        print("\nNo alignment sessions found in log file.")
        print("Make sure you selected the correct model and the log contains alignment data.")
        return

    # Print metrics for each session
    for i, session in enumerate(sessions):
        analyzer.print_metrics(session, session_num=i+1)

    # Generate plots if requested
    if args.plot or args.save_plots:
        for i, session in enumerate(sessions):
            if args.save_plots:
                save_dir = Path(args.save_plots)
                save_dir.mkdir(exist_ok=True)
                save_path = save_dir / f"session_{i+1}.png"
                analyzer.plot_session(session, save_path=str(save_path))
            else:
                analyzer.plot_session(session)

    # Export metrics if requested
    if args.export:
        analyzer.export_metrics_json(sessions, args.export)

    # Print summary statistics
    if len(sessions) > 1:
        print(f"\n{'='*70}")
        print(f"Summary Statistics ({len(sessions)} sessions)")
        print(f"{'='*70}")

        success_rate = sum(s.metrics.success for s in sessions) / len(sessions) * 100
        print(f"Success Rate: {success_rate:.1f}%")

        alignment_times = [s.metrics.alignment_time for s in sessions if s.metrics.alignment_time]
        if alignment_times:
            print(f"Avg Alignment Time: {np.mean(alignment_times):.3f} s (±{np.std(alignment_times):.3f} s)")

        settling_times = [s.metrics.settling_time for s in sessions if s.metrics.settling_time]
        if settling_times:
            print(f"Avg Settling Time: {np.mean(settling_times):.3f} s (±{np.std(settling_times):.3f} s)")


if __name__ == "__main__":
    main()
