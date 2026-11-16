import React, { useMemo, useState } from 'react';
import { Check, Copy, Download, FileCode2 } from 'lucide-react';
import { ANDROID_SOURCE_FILES, AndroidSourceFile } from '../engine/androidFiles';

const LAYERS: Array<'All' | AndroidSourceFile['layer']> = [
  'All',
  'Domain',
  'Data',
  'Rendering',
  'UI',
  'Tests',
  'Gradle',
];

export const KotlinSourceExplorer: React.FC = () => {
  const [layerFilter, setLayerFilter] = useState<'All' | AndroidSourceFile['layer']>('All');
  const [selectedId, setSelectedId] = useState<string>(ANDROID_SOURCE_FILES[0].id);
  const [copied, setCopied] = useState<boolean>(false);

  const visibleFiles = useMemo(() => {
    if (layerFilter === 'All') return ANDROID_SOURCE_FILES;
    return ANDROID_SOURCE_FILES.filter((f) => f.layer === layerFilter);
  }, [layerFilter]);

  const activeFile = useMemo(
    () => ANDROID_SOURCE_FILES.find((f) => f.id === selectedId) ?? ANDROID_SOURCE_FILES[0],
    [selectedId]
  );

  const lines = useMemo(() => activeFile.content.split('\n'), [activeFile.content]);

  const handleCopy = async () => {
    await navigator.clipboard.writeText(activeFile.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  const handleDownload = () => {
    const blob = new Blob([activeFile.content], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = activeFile.name.split('/').pop() ?? activeFile.name;
    a.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-[310px_1fr] gap-4 items-start">
      <div className="bg-[#13161D] border border-[#242936] rounded-xl overflow-hidden">
        <div className="p-3 border-b border-[#242936] space-y-2.5">
          <div className="flex items-center justify-between text-xs text-slate-400">
            <span className="font-medium text-slate-200">com.gima.gve</span>
            <span className="font-mono tabular-nums">{visibleFiles.length} files</span>
          </div>
          <div className="flex flex-wrap gap-1">
            {LAYERS.map((layer) => (
              <button
                key={layer}
                type="button"
                onClick={() => setLayerFilter(layer)}
                className={`px-2 py-1 rounded-md text-[11px] font-medium transition-colors cursor-pointer whitespace-nowrap ${
                  layerFilter === layer
                    ? 'bg-teal-500/20 text-teal-300'
                    : 'text-slate-400 hover:text-slate-200 hover:bg-[#1E2330]'
                }`}
              >
                {layer}
              </button>
            ))}
          </div>
        </div>

        <div className="divide-y divide-[#1E2330] max-h-[600px] overflow-y-auto">
          {visibleFiles.map((file) => {
            const isSelected = file.id === activeFile.id;
            return (
              <button
                key={file.id}
                type="button"
                onClick={() => setSelectedId(file.id)}
                className={`w-full text-left px-3.5 py-2.5 flex items-start gap-2.5 transition-colors cursor-pointer ${
                  isSelected ? 'bg-[#1A202C] text-white' : 'text-slate-300 hover:bg-[#171B24]'
                }`}
              >
                <FileCode2
                  className={`w-4 h-4 mt-0.5 shrink-0 ${
                    isSelected ? 'text-teal-400' : 'text-slate-500'
                  }`}
                />
                <div className="min-w-0 flex-1">
                  <div className="text-xs font-medium truncate">{file.name}</div>
                  <div className="text-[11px] text-slate-500 truncate mt-0.5">{file.layer}</div>
                </div>
              </button>
            );
          })}
        </div>
      </div>

      <div className="bg-[#13161D] border border-[#242936] rounded-xl overflow-hidden min-w-0">
        <div className="flex flex-wrap items-center justify-between gap-3 px-4 py-3 border-b border-[#242936] bg-[#101319]">
          <div className="min-w-0">
            <div className="text-xs font-mono text-teal-300 truncate">android/{activeFile.path}</div>
            <div className="text-xs text-slate-400 mt-0.5">{activeFile.summary}</div>
          </div>
          <div className="flex items-center gap-2 shrink-0">
            <button
              type="button"
              onClick={handleCopy}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-medium text-slate-200 hover:bg-[#222836] transition-colors cursor-pointer whitespace-nowrap"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-teal-400" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copied' : 'Copy'}</span>
            </button>
            <button
              type="button"
              onClick={handleDownload}
              className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-[#2A3040] bg-[#1A1E28] text-xs font-medium text-slate-200 hover:bg-[#222836] transition-colors cursor-pointer whitespace-nowrap"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Download</span>
            </button>
          </div>
        </div>

        <div className="max-h-[600px] overflow-auto bg-[#0B0D11] p-4 font-mono text-xs leading-5">
          <table className="w-full border-collapse">
            <tbody>
              {lines.map((line, index) => (
                <tr key={index} className="hover:bg-[#13161D]">
                  <td className="select-none pr-4 text-right text-slate-600 tabular-nums w-10 align-top">
                    {index + 1}
                  </td>
                  <td className="text-slate-200 whitespace-pre">{line}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
