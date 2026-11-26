import React from 'react';
import type { Piece } from '../chess/fideRules';

export type PieceStyle = 'staunton' | 'neo';

interface PieceSvgProps {
  piece: Piece;
  style?: PieceStyle;
  className?: string;
}

export const ChessPieceSvg: React.FC<PieceSvgProps> = ({
  piece,
  style = 'staunton',
  className = 'w-full h-full select-none pointer-events-none',
}) => {
  const isWhite = piece.color === 'w';
  const fill = isWhite ? (style === 'neo' ? '#F6F3EC' : '#FFFFFF') : (style === 'neo' ? '#20242B' : '#1E1E24');
  const stroke = isWhite ? '#1B1B1F' : '#111115';
  const detail = isWhite ? '#1B1B1F' : '#D4D4DC';

  if (style === 'neo') {
    return (
      <svg viewBox="0 0 45 45" className={className} aria-hidden="true">
        {piece.type === 'p' && (
          <g fill={fill} stroke={stroke} strokeWidth="1.6" strokeLinejoin="round">
            <circle cx="22.5" cy="14" r="4.5" />
            <path d="M18.5,18.5 L26.5,18.5 L28.5,31 L16.5,31 Z" />
            <path d="M13,34 L32,34 L33.5,38.5 L11.5,38.5 Z" />
          </g>
        )}
        {piece.type === 'r' && (
          <path
            fill={fill}
            stroke={stroke}
            strokeWidth="1.6"
            strokeLinejoin="round"
            d="M12,9.5 L16.5,9.5 L16.5,12.5 L20.5,12.5 L20.5,9.5 L24.5,9.5 L24.5,12.5 L28.5,12.5 L28.5,9.5 L33,9.5 L33,16 L30,19 L30,31.5 L33,34.5 L33,38.5 L12,38.5 L12,34.5 L15,31.5 L15,19 L12,16 Z"
          />
        )}
        {piece.type === 'n' && (
          <g>
            <path
              fill={fill}
              stroke={stroke}
              strokeWidth="1.6"
              strokeLinejoin="round"
              d="M14,38.5 L34,38.5 L34,34 C34,21 30.5,12.5 22.5,9.5 L19.5,6.5 L17.5,10 C13,12 9.5,18.5 8.5,24 C8.2,26 10.5,27.5 13,26.5 L18.5,22.5 C19.5,25.5 17,29 14,34 Z"
            />
            <circle cx="14.3" cy="15.5" r="1.2" fill={detail} />
          </g>
        )}
        {piece.type === 'b' && (
          <g>
            <circle cx="22.5" cy="8" r="2" fill={fill} stroke={stroke} strokeWidth="1.5" />
            <path
              fill={fill}
              stroke={stroke}
              strokeWidth="1.6"
              strokeLinejoin="round"
              d="M22.5,11 C15.5,15 14,23.5 17,29.5 L14,34 L12,38.5 L33,38.5 L31,34 L28,29.5 C31,23.5 29.5,15 22.5,11 Z"
            />
            <path stroke={detail} strokeWidth="1.5" strokeLinecap="round" d="M19.5,21.5 L25.5,16.5 M17,30.5 L28,30.5" />
          </g>
        )}
        {piece.type === 'q' && (
          <g fill={fill} stroke={stroke} strokeWidth="1.5" strokeLinejoin="round">
            <path d="M8,14 L14,26 L16,11 L22.5,24 L29,11 L31,26 L37,14 L33,32.5 L33,38.5 L12,38.5 L12,32.5 Z" />
            <circle cx="7.2" cy="12.5" r="1.8" />
            <circle cx="15.7" cy="9.5" r="1.8" />
            <circle cx="29.2" cy="9.5" r="1.8" />
            <circle cx="37.8" cy="12.5" r="1.8" />
          </g>
        )}
        {piece.type === 'k' && (
          <g>
            <path stroke={stroke} strokeWidth="2" strokeLinecap="round" d="M22.5,6 L22.5,12.5 M19.5,9 L25.5,9" />
            <path
              fill={fill}
              stroke={stroke}
              strokeWidth="1.6"
              strokeLinejoin="round"
              d="M12,38.5 L33,38.5 L33,31 C38,27 38.5,18.5 32.5,16 C28.5,14.5 24.5,17 22.5,20.5 C20.5,17 16.5,14.5 12.5,16 C6.5,18.5 7,27 12,31 Z"
            />
            <path stroke={detail} strokeWidth="1.3" d="M12.5,33 L32.5,33" />
          </g>
        )}
      </svg>
    );
  }

  return (
    <svg viewBox="0 0 45 45" className={className} aria-hidden="true">
      {piece.type === 'p' && (
        <g fill={fill} stroke={stroke} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M22.5,9 C19.5,9 17.5,11.2 17.5,14 C17.5,15.6 18.2,17 19.3,17.9 C16.9,19.1 15.5,21.5 15.5,24.5 C15.5,26.8 16.6,28.7 18.2,29.8 L14.8,34.5 L12.5,36.5 L32.5,36.5 L30.2,34.5 L26.8,29.8 C28.4,28.7 29.5,26.8 29.5,24.5 C29.5,21.5 28.1,19.1 25.7,17.9 C26.8,17 27.5,15.6 27.5,14 C27.5,11.2 25.5,9 22.5,9 Z" />
          <rect x="11.5" y="36.5" width="22" height="3" />
        </g>
      )}
      {piece.type === 'r' && (
        <g fill={fill} stroke={stroke} strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
          <path d="M9,39 L36,39 L36,36 L9,36 Z M12,36 L12,32 L33,32 L33,36 Z M14,29.5 L14,16.5 L31,16.5 L31,29.5 Z M14,16.5 L11,14 L34,14 L31,16.5 Z M11,14 L11,9 L15,9 L15,11 L20,11 L20,9 L25,9 L25,11 L30,11 L30,9 L34,9 L34,14 Z M31,29.5 L32.5,32 L12.5,32 L14,29.5 Z" />
          {!isWhite && (
            <path stroke={detail} strokeWidth="1" d="M12,35.5 L33,35.5 M14,29.5 L31,29.5 M14,16.5 L31,16.5" />
          )}
        </g>
      )}
      {piece.type === 'n' && (
        <g>
          <path
            fill={fill}
            stroke={stroke}
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M22,10 C32.5,11 38.5,18 38,39 L15,39 C15,30 25,32.5 23,18 Z"
          />
          <path
            fill={fill}
            stroke={stroke}
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M24,18 C24.38,20.91 18.45,25.37 16,27 C13,29 13.18,31.34 11,31 C9.96,30.06 12.41,27.96 11,28 C10,28 11.19,29.23 10,30 C9,30 5.997,31 6,26 C6,24 12,14 12,14 C12,14 13.89,12.1 14,10.5 C13.27,9.5 13.5,8.5 13.5,7.5 C14.5,6.5 16.5,10 16.5,10 L18.5,10 C18.5,10 19.28,8.01 21,7 C22,7 22,10 22,10"
          />
          <circle cx="8.9" cy="25.5" r="0.7" fill={detail} />
          <circle cx="14.6" cy="15.5" r="1" fill={detail} />
        </g>
      )}
      {piece.type === 'b' && (
        <g>
          <path
            fill={fill}
            stroke={stroke}
            strokeWidth="1.5"
            strokeLinejoin="round"
            d="M9,36 C12.39,35.03 19.11,36.43 22.5,34 C25.89,36.43 32.61,35.03 36,36 C36,36 37.65,36.54 39,38 C38.32,38.97 37.35,38.99 36,38.5 C32.61,37.53 25.89,38.96 22.5,37.5 C19.11,38.96 12.39,37.53 9,38.5 C7.65,38.99 6.68,38.97 6,38 C7.35,36.54 9,36 9,36 Z"
          />
          <path
            fill={fill}
            stroke={stroke}
            strokeWidth="1.5"
            strokeLinejoin="round"
            d="M15,32 C17.5,34.5 27.5,34.5 30,32 C30.5,30.5 30,30 30,30 C30,27.5 27.5,26 27.5,26 C33,24.5 33.5,14.5 22.5,10.5 C11.5,14.5 12,24.5 17.5,26 C17.5,26 15,27.5 15,30 C15,30 14.5,30.5 15,32 Z"
          />
          <circle cx="22.5" cy="8" r="2.5" fill={fill} stroke={stroke} strokeWidth="1.5" />
          <path stroke={detail} strokeWidth="1.3" strokeLinecap="round" d="M17.5,26 L27.5,26 M15,30 L30,30 M22.5,15.5 L22.5,20.5 M20,18 L25,18" />
        </g>
      )}
      {piece.type === 'q' && (
        <g>
          <path
            fill={fill}
            stroke={stroke}
            strokeWidth="1.5"
            strokeLinejoin="round"
            d="M9,26 C17.5,24.5 30,24.5 36,26 L38.5,13.5 L31,25 L30.7,10.9 L25.5,24.5 L22.5,10 L19.5,24.5 L14.3,10.9 L14,25 L6.5,13.5 L9,26 Z"
          />
          <path
            fill={fill}
            stroke={stroke}
            strokeWidth="1.5"
            strokeLinejoin="round"
            d="M9,26 C9,28 10.5,28 11.5,30 C12.5,31.5 12.5,31 12,33.5 C10.5,34.5 10.5,36 10.5,36 C9,37.5 11,38.5 11,38.5 C17.5,39.5 27.5,39.5 34,38.5 C34,38.5 35.5,37.5 34,36 C34,36 34.5,34.5 33,33.5 C32.5,31 32.5,31.5 33.5,30 C34.5,28 36,28 36,26 C27.5,24.5 17.5,24.5 9,26 Z"
          />
          {[
            [6, 12],
            [14, 9],
            [22.5, 8],
            [31, 9],
            [39, 12],
          ].map(([cx, cy]) => (
            <circle key={`${cx}-${cy}`} cx={cx} cy={cy} r="2" fill={fill} stroke={stroke} strokeWidth="1.4" />
          ))}
          <path stroke={detail} strokeWidth="1.2" strokeLinecap="round" d="M11.5,30 C15,29 30,29 33.5,30 M12,33.5 C18,32.5 27,32.5 33,33.5" />
        </g>
      )}
      {piece.type === 'k' && (
        <g>
          <path stroke={stroke} strokeWidth="1.5" strokeLinecap="round" d="M22.5,11.63 L22.5,6 M20,8 L25,8" />
          <path
            fill={fill}
            stroke={stroke}
            strokeWidth="1.5"
            d="M22.5,25 C22.5,25 27,17.5 25.5,14.5 C25.5,14.5 24.5,12 22.5,12 C20.5,12 19.5,14.5 19.5,14.5 C18,17.5 22.5,25 22.5,25"
          />
          <path
            fill={fill}
            stroke={stroke}
            strokeWidth="1.5"
            strokeLinecap="round"
            strokeLinejoin="round"
            d="M11.5,37 C17,40.5 27,40.5 32.5,37 L32.5,30 C32.5,30 41.5,25.5 38.5,19.5 C34.5,13 25,16 22.5,23.5 L22.5,27 L22.5,23.5 C19,16 9.5,13 6.5,19.5 C3.5,25.5 11.5,29.5 11.5,29.5 L11.5,37 Z"
          />
          <path stroke={detail} strokeWidth="1.2" strokeLinecap="round" d="M11.5,30 C17,27 27,27 32.5,30 M11.5,33.5 C17,30.5 27,30.5 32.5,33.5 M11.5,37 C17,34 27,34 32.5,37" />
        </g>
      )}
    </svg>
  );
};
