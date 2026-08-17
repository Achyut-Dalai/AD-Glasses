import {useEffect, useState} from 'react';
import {ADNative, DashboardState, fallbackDashboard} from '../native/ADNative';

/**
 * The native runtime is still the source of truth during the brownfield migration.
 * Polling is intentionally modest and can be replaced by native events once the runtime
 * controller is extracted from MainActivity.
 */
export function useDashboardState(intervalMs = 1500): DashboardState {
  const [state, setState] = useState<DashboardState>(fallbackDashboard);

  useEffect(() => {
    let active = true;
    const refresh = () => ADNative.dashboard().then(next => {
      if (active) setState(next);
    });
    void refresh();
    const timer = setInterval(refresh, intervalMs);
    return () => {
      active = false;
      clearInterval(timer);
    };
  }, [intervalMs]);

  return state;
}
