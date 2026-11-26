package com.chessoffline.app.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.chessoffline.app.R
import com.chessoffline.app.data.BoardThemeId
import com.chessoffline.app.data.PieceStyleId
import com.chessoffline.core.model.Piece
import com.chessoffline.core.model.PieceColor
import com.chessoffline.core.model.PieceType

data class BoardPalette(
    val lightSquare: Color,
    val darkSquare: Color,
    val lastMoveLight: Color,
    val lastMoveDark: Color,
    val selectedSquare: Color,
    val legalDotColor: Color,
    val coordOnLight: Color,
    val coordOnDark: Color,
    val boardBorder: Color,
    val hintArrowColor: Color
)

object BoardPalettes {
    val Wood = BoardPalette(
        lightSquare = Color(0xFFF0D9B5),
        darkSquare = Color(0xFFB58863),
        lastMoveLight = Color(0xFFF6F680),
        lastMoveDark = Color(0xFFBACB44),
        selectedSquare = Color(0xFF829769),
        legalDotColor = Color(0x55222222),
        coordOnLight = Color(0xFF9E704D),
        coordOnDark = Color(0xFFF0D9B5),
        boardBorder = Color(0xFF5D3A21),
        hintArrowColor = Color(0xCC2563EB)
    )

    val Tournament = BoardPalette(
        lightSquare = Color(0xFFEEEED2),
        darkSquare = Color(0xFF769656),
        lastMoveLight = Color(0xFFF6F680),
        lastMoveDark = Color(0xFFBACA2B),
        selectedSquare = Color(0xFF5A7F3B),
        legalDotColor = Color(0x551F2E14),
        coordOnLight = Color(0xFF5F7D42),
        coordOnDark = Color(0xFFEEEED2),
        boardBorder = Color(0xFF3E552A),
        hintArrowColor = Color(0xCCF59E0B)
    )

    val DarkSlate = BoardPalette(
        lightSquare = Color(0xFF94A3B8),
        darkSquare = Color(0xFF334155),
        lastMoveLight = Color(0xFF60A5FA).copy(alpha = 0.55f),
        lastMoveDark = Color(0xFF2563EB).copy(alpha = 0.55f),
        selectedSquare = Color(0xFF0EA5E9).copy(alpha = 0.65f),
        legalDotColor = Color(0x66E2E8F0),
        coordOnLight = Color(0xFF1E293B),
        coordOnDark = Color(0xFFCBD5E1),
        boardBorder = Color(0xFF1E293B),
        hintArrowColor = Color(0xCC10B981)
    )

    fun forTheme(id: BoardThemeId): BoardPalette = when (id) {
        BoardThemeId.WOOD -> Wood
        BoardThemeId.TOURNAMENT -> Tournament
        BoardThemeId.DARK_SLATE -> DarkSlate
    }
}

@DrawableRes
fun Piece.drawableRes(style: PieceStyleId): Int = when (style) {
    PieceStyleId.STAUNTON -> when (color) {
        PieceColor.WHITE -> when (type) {
            PieceType.PAWN -> R.drawable.ic_wp
            PieceType.KNIGHT -> R.drawable.ic_wn
            PieceType.BISHOP -> R.drawable.ic_wb
            PieceType.ROOK -> R.drawable.ic_wr
            PieceType.QUEEN -> R.drawable.ic_wq
            PieceType.KING -> R.drawable.ic_wk
        }
        PieceColor.BLACK -> when (type) {
            PieceType.PAWN -> R.drawable.ic_bp
            PieceType.KNIGHT -> R.drawable.ic_bn
            PieceType.BISHOP -> R.drawable.ic_bb
            PieceType.ROOK -> R.drawable.ic_br
            PieceType.QUEEN -> R.drawable.ic_bq
            PieceType.KING -> R.drawable.ic_bk
        }
    }
    PieceStyleId.NEO -> when (color) {
        PieceColor.WHITE -> when (type) {
            PieceType.PAWN -> R.drawable.ic_neo_wp
            PieceType.KNIGHT -> R.drawable.ic_neo_wn
            PieceType.BISHOP -> R.drawable.ic_neo_wb
            PieceType.ROOK -> R.drawable.ic_neo_wr
            PieceType.QUEEN -> R.drawable.ic_neo_wq
            PieceType.KING -> R.drawable.ic_neo_wk
        }
        PieceColor.BLACK -> when (type) {
            PieceType.PAWN -> R.drawable.ic_neo_bp
            PieceType.KNIGHT -> R.drawable.ic_neo_bn
            PieceType.BISHOP -> R.drawable.ic_neo_bb
            PieceType.ROOK -> R.drawable.ic_neo_br
            PieceType.QUEEN -> R.drawable.ic_neo_bq
            PieceType.KING -> R.drawable.ic_neo_bk
        }
    }
}
