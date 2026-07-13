import React, { useEffect, useRef, useState } from "react";
import { ChevronDown, Check } from "lucide-react";

export interface DropdownOption {
  value: string | number;
  label: string;
  disabled?: boolean;
}

interface DropdownProps {
  value: string | number | "";
  onChange: (value: string) => void;
  options: DropdownOption[];
  icon?: React.ReactNode;
  placeholder?: string;
  title?: string;
  minWidth?: number;
  disabled?: boolean;
  style?: React.CSSProperties;
}

export const Dropdown: React.FC<DropdownProps> = ({
  value,
  onChange,
  options,
  icon,
  placeholder = "Seçin...",
  title,
  minWidth = 140,
  disabled = false,
  style,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [highlighted, setHighlighted] = useState(-1);
  const rootRef = useRef<HTMLDivElement>(null);

  const selected = options.find((o) => String(o.value) === String(value));

  useEffect(() => {
    if (!isOpen) return;
    const handleClickOutside = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setIsOpen(false);
      }
    };
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [isOpen]);

  useEffect(() => {
    if (isOpen) {
      const idx = options.findIndex((o) => String(o.value) === String(value));
      setHighlighted(idx);
    }
  }, [isOpen, value, options]);

  const commit = (option: DropdownOption) => {
    if (option.disabled) return;
    onChange(String(option.value));
    setIsOpen(false);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (disabled) return;
    switch (e.key) {
      case "Enter":
      case " ":
        e.preventDefault();
        if (isOpen && highlighted >= 0) {
          commit(options[highlighted]);
        } else {
          setIsOpen((o) => !o);
        }
        break;
      case "ArrowDown":
        e.preventDefault();
        if (!isOpen) {
          setIsOpen(true);
        } else {
          setHighlighted((h) => Math.min(h + 1, options.length - 1));
        }
        break;
      case "ArrowUp":
        e.preventDefault();
        if (isOpen) setHighlighted((h) => Math.max(h - 1, 0));
        break;
      case "Escape":
        setIsOpen(false);
        break;
    }
  };

  return (
    <div
      ref={rootRef}
      style={{ ...styles.root, minWidth, opacity: disabled ? 0.6 : 1, ...style }}
      title={title}
    >
      <button
        type="button"
        style={styles.trigger}
        onClick={() => !disabled && setIsOpen((o) => !o)}
        onKeyDown={handleKeyDown}
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
      >
        {icon && <span style={styles.icon}>{icon}</span>}
        <span style={{ ...styles.label, color: selected ? "var(--text-primary)" : "var(--text-muted)" }}>
          {selected ? selected.label : placeholder}
        </span>
        <ChevronDown
          size={16}
          color="var(--text-secondary)"
          style={{
            transition: "transform 0.2s ease",
            transform: isOpen ? "rotate(180deg)" : "rotate(0deg)",
            flexShrink: 0,
          }}
        />
      </button>

      {isOpen && (
        <ul style={styles.menu} role="listbox">
          {options.length === 0 && (
            <li style={{ ...styles.option, color: "var(--text-muted)", cursor: "default" }}>—</li>
          )}
          {options.map((option, idx) => {
            const isSelected = String(option.value) === String(value);
            const isHighlighted = idx === highlighted;
            return (
              <li
                key={option.value}
                role="option"
                aria-selected={isSelected}
                onMouseEnter={() => setHighlighted(idx)}
                onClick={() => commit(option)}
                style={{
                  ...styles.option,
                  background: isHighlighted ? "var(--bg-tertiary)" : "transparent",
                  color: option.disabled
                    ? "var(--text-muted)"
                    : isSelected
                    ? "var(--neon-blue)"
                    : "var(--text-primary)",
                  cursor: option.disabled ? "not-allowed" : "pointer",
                }}
              >
                <span style={styles.optionLabel}>{option.label}</span>
                {isSelected && <Check size={14} color="var(--neon-blue)" style={{ flexShrink: 0 }} />}
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
};

const styles: Record<string, React.CSSProperties> = {
  root: {
    position: "relative",
    display: "inline-block",
    fontFamily: "var(--font-sans)",
  },
  trigger: {
    display: "flex",
    alignItems: "center",
    gap: "8px",
    width: "100%",
    background: "var(--bg-tertiary)",
    border: "1px solid var(--glass-border)",
    borderRadius: "8px",
    padding: "6px 12px",
    color: "var(--text-primary)",
    fontFamily: "var(--font-sans)",
    fontSize: "0.85rem",
    fontWeight: 500,
    cursor: "pointer",
    outline: "none",
    transition: "var(--transition-smooth)",
  },
  icon: {
    display: "flex",
    alignItems: "center",
    flexShrink: 0,
  },
  label: {
    flex: 1,
    textAlign: "left",
    whiteSpace: "nowrap",
    overflow: "hidden",
    textOverflow: "ellipsis",
  },
  menu: {
    position: "absolute",
    top: "calc(100% + 6px)",
    left: 0,
    right: 0,
    zIndex: 1000,
    listStyle: "none",
    margin: 0,
    padding: "6px",
    background: "var(--bg-secondary)",
    border: "1px solid var(--glass-border)",
    borderRadius: "10px",
    boxShadow: "0 12px 30px rgba(0, 0, 0, 0.45)",
    maxHeight: "280px",
    overflowY: "auto",
    backdropFilter: "var(--glass-blur)",
  },
  option: {
    display: "flex",
    alignItems: "center",
    justifyContent: "space-between",
    gap: "8px",
    padding: "8px 10px",
    borderRadius: "6px",
    fontSize: "0.85rem",
    fontWeight: 500,
    transition: "background 0.15s ease",
  },
  optionLabel: {
    whiteSpace: "nowrap",
    overflow: "hidden",
    textOverflow: "ellipsis",
  },
};
