import {useCallback, useEffect, useState} from 'react';
import {
  ADProductSettings,
  defaultProductSettings,
  ProductSettings,
} from '../native/ADProductSettings';

export function useProductSettings() {
  const [settings, setSettings] = useState<ProductSettings>(defaultProductSettings);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    const next = await ADProductSettings.read();
    setSettings(next);
    setLoading(false);
    return next;
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return {settings, loading, refresh, setSettings};
}
